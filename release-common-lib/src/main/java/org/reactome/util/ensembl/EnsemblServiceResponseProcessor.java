package org.reactome.util.ensembl;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This is used to process web service responses from ENSEMBL. This code was copied from AddLinks, org.reactome.addlinks.dataretrieval.ensembl.EnsemblServiceResponseProcessor
 * Eventually, common code from AddLinks (like this class) should be moved into release-common-lib and then include release-common-lib as a dependency.
 * @author sshorser
 *
 */
public final class EnsemblServiceResponseProcessor
{

	public class EnsemblServiceResult
	{
		private Duration waitTime = Duration.ZERO;
		private String result;
		private boolean okToRetry;
		private int status;
		
		public Duration getWaitTime()
		{
			return this.waitTime;
		}
		public void setWaitTime(Duration waitTime)
		{
			this.waitTime = waitTime;
		}
		public String getResult()
		{
			return this.result;
		}
		public void setResult(String result)
		{
			this.result = result;
		}
		public boolean isOkToRetry()
		{
			return this.okToRetry;
		}
		public void setOkToRetry(boolean okToRetry)
		{
			this.okToRetry = okToRetry;
		}
		public int getStatus()
		{
			return this.status;
		}
		public void setStatus(int status)
		{
			this.status = status;
		}
	}
	
	private int waitMultiplier = 1;
	
	// Assume a quote of 10 to start. This will get set properly with ever response from the service.
	private static final AtomicInteger numRequestsRemaining = new AtomicInteger(10);
	
	private Logger logger ;
	
	// This can't be static because each request could have a different timeoutRetries counter.
	private int timeoutRetriesRemaining = 3;
	
	public EnsemblServiceResponseProcessor(Logger logger)
	{
		if (logger!=null)
		{
			this.logger = logger;
		}
		else
		{
			this.logger = LogManager.getLogger();
		}
	}
	
	public EnsemblServiceResponseProcessor()
	{
		this(null);
	}
	
	
	public EnsemblServiceResult processResponse(HttpResponse response, URI originalURI)
	{
		EnsemblServiceResult result = this.new EnsemblServiceResult();
		result.setStatus(response.getStatusLine().getStatusCode());
		boolean okToQuery = false;
		// First check to see if we got a "Retry-After" header. This is most likely to happen if we send SO many requests
		// that we used up our quota with the service, and need to wait for it to reset.
		if ( response.containsHeader("Retry-After") )
		{
			logger.debug("Response message: {} ; Reason code: {}; Headers: {}", response.getStatusLine().toString(),
																				response.getStatusLine().getReasonPhrase(),
										Arrays.stream(response.getAllHeaders()).map( h -> h.toString()).collect(Collectors.toList()));
			
			
			Duration waitTime = Duration.ofSeconds(Integer.valueOf(response.getHeaders("Retry-After")[0].getValue().toString()));
			
			logger.warn("The server told us to wait, so we will wait for {} * {} before trying again.",waitTime, waitMultiplier);
			
			result.setWaitTime(waitTime.multipliedBy(this.waitMultiplier));
			this.waitMultiplier ++;
			// If we get told to wait > 5 times, let's just take the hint and stop trying.
			if (this.waitMultiplier >= 5)
			{
				logger.error("I've already waited {} times and I'm STILL getting told to wait. This will be the LAST attempt.");
				okToQuery = false;
			}
			else
			{
				// It's ok to re-query the sevice, as long as you wait for the time the server wants you to wait.
				okToQuery = true;
			}
		}
		// Else... no "Retry-After" so we haven't gone over our quota.
		else
		{
			String content = "";
			switch (response.getStatusLine().getStatusCode())
			{
				case HttpStatus.SC_OK:
					try
					{
						//ContentType.get(response.getEntity()).getCharset().name();
						content = EntityUtils.toString(response.getEntity(), Charset.forName("UTF-8"));
					}
					catch (ParseException e)
					{
						e.printStackTrace();
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}
					result.setResult(content);
					okToQuery = false;
					break;
				case HttpStatus.SC_NOT_FOUND:
					logger.error("Response code 404 (\"Not found\") received: ", response.getStatusLine().getReasonPhrase() );
					// If we got 404, don't retry.
					okToQuery = false;
					break;
				case HttpStatus.SC_INTERNAL_SERVER_ERROR:
					logger.error("Error 500 detected! Message: {}",response.getStatusLine().getReasonPhrase());
					// If we get 500 error then we should just get  out of here. Maybe throw an exception?
					okToQuery = false;
					break;
				case HttpStatus.SC_BAD_REQUEST:
					String s = "";
					try
					{
						s = EntityUtils.toString(response.getEntity());
					}
					catch (ParseException e)
					{
						e.printStackTrace();
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}
					logger.trace("Response code was 400 (\"Bad request\"). Message from server: {}", s);
					okToQuery = false;
					break;
				case HttpStatus.SC_GATEWAY_TIMEOUT:
					timeoutRetriesRemaining--;
					logger.error("Request timed out! {} retries remaining", timeoutRetriesRemaining);
					if (timeoutRetriesRemaining > 0)
					{
						okToQuery = true;
					}
					else
					{
						logger.error("No more retries remaining.");
						timeoutRetriesRemaining = 3;
						okToQuery = false;
					}
					break;
				default:
					// Log any other kind of response.
					okToQuery = false;
					try
					{
						content = EntityUtils.toString(response.getEntity(), Charset.forName("UTF-8"));
					}
					catch (ParseException e)
					{
						e.printStackTrace();
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}
					result.setResult(content);
					logger.info("Unexpected response {} with message: {}",response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
					break;
			}
		}
		result.setOkToRetry(okToQuery);
		if (response.containsHeader("X-RateLimit-Remaining"))
		{
			int numRequestsRemaining = Integer.valueOf(response.getHeaders("X-RateLimit-Remaining")[0].getValue().toString());
			EnsemblServiceResponseProcessor.numRequestsRemaining.set(numRequestsRemaining);
			numRequestsRemaining = EnsemblServiceResponseProcessor.numRequestsRemaining.get();
			if (numRequestsRemaining % 1000 == 0)
			{
				logger.debug("{} requests remaining", numRequestsRemaining);
			}
		}
		else
		{
			// actually, this is not so strange - I think that http://rest.ensemblgenomes.org/ *never* returns X-RateLimit-Remaining; I think that header *only* comes from rest.ensembl.org
			// So only log a message if we didn't get a rate limit from rest.ensembl.org - if it didn't come from rest.ensemblGENOMES.org, that's OK.
			if (!originalURI.toString().contains("rest.ensemblgenomes.org"))
			{
				logger.warn("No X-RateLimit-Remaining was returned. This is odd. Response message: {} ; Headers returned are: {}\nLast known value for remaining was {}", response.getStatusLine().toString(), Arrays.stream(response.getAllHeaders()).map( h -> h.toString()).collect(Collectors.toList()), EnsemblServiceResponseProcessor.numRequestsRemaining);
			}
		}
		return result;
	}
	
	public static int getNumRequestsRemaining()
	{
		return EnsemblServiceResponseProcessor.numRequestsRemaining.get();
	}
}