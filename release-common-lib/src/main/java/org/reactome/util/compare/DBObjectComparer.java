package org.reactome.util.compare;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.Instance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;

/**
 * This class can be used to perform comparisons on any two DatabaseObjects across two different databases.
 * (It assumes that they have the same DB_ID).
 * @author sshorser
 *
 */
public class DBObjectComparer
{
	/**
	 * Compares two GKInstances.
	 * @param inst1 - the first instance.
	 * @param inst2 - the second instance.
	 * @param sb - a StringBuilder that will contain a detailed report of differences.
	 * @return The number of differences between the two instances.
	 * A single-valued attribute that differs will count as 1 diff.
	 * If the instances have different schema classes, that will count as 1 diff.
	 * If a multi-valued attribute has a different number of elements between the two intances, 
	 * that will count as 1 diff and the elements will NOT be compared.
	 */
	public static int compareInstances(GKInstance inst1, GKInstance inst2, StringBuilder sb)
	{
		return compareInstances(inst1, inst2, sb, 5, false);
	}
	
	/**
	 * Compares two GKInstances.
	 * @param inst1 - the first instance.
	 * @param inst2 - the second instance.
	 * @param sb - a StringBuilder that will contain a detailed report of differences.
	 * @param checkReferrers - Should referring instances also be checked? If <b>true</b>, then referring attributes will <em>also</em> be checked for differences.
	 * They will be followed to the same recursion depth as regular attributes. Using this with a high maxRecursionDepth could lead to a very long execution time. Be careful!!
	 * You might also want to use a custom predicate to filter out attributes that could lead to a cyclical difference check.
	 * @return The number of differences between the two instances.
	 * A single-valued attribute that differs will count as 1 diff.
	 * If the instances have different schema classes, that will count as 1 diff.
	 * If a multi-valued attribute has a different number of elements between the two intances, 
	 * that will count as 1 diff and the elements will NOT be compared.
	 */
	public static int compareInstances(GKInstance inst1, GKInstance inst2, StringBuilder sb, boolean checkReferrers)
	{
		return compareInstances(inst1, inst2, sb, 5, checkReferrers);
	}
	
	/**
	 * Compares two GKInstances.
	 * @param inst1 - the first instance.
	 * @param inst2 - the second instance.
	 * @param sb - a StringBuilder that will contain a detailed report of differences.
	 * @param maxRecursionDepth - the maximum depth of recursion that will be allowed. Normally a depth of 2 or 3 is probably sufficient.
	 * @param checkReferrers - Should referring instances also be checked? If <b>true</b>, then referring attributes will <em>also</em> be checked for differences.
	 * They will be followed to the same recursion depth as regular attributes. Using this with a high maxRecursionDepth could lead to a very long execution time. Be careful!!
	 * You might also want to use a custom predicate to filter out attributes that could lead to a cyclical difference check.
	 * @return The number of differences between the two instances.
	 * A single-valued attribute that differs will count as 1 diff.
	 * If the instances have different schema classes, that will count as 1 diff.
	 * If a multi-valued attribute has a different number of elements between the two intances, 
	 * that will count as 1 diff and the elements will NOT be compared.
	 */
	public static int compareInstances(GKInstance inst1, GKInstance inst2, StringBuilder sb, int maxRecursionDepth, boolean checkReferrers)
	{
		return compareInstances(inst1, inst2, sb, 0, 0, maxRecursionDepth, null, checkReferrers);
	}
	
	/**
	 * Compares two GKInstances.
	 * @param inst1 - the first instance.
	 * @param inst2 - the second instance.
	 * @param sb - a StringBuilder that will contain a detailed report of differences.
	 * @param maxRecursionDepth - the maximum depth of recursion that will be allowed. Normally a depth of 2 or 3 is probably sufficient.
	 * @param customAttributeNameFilter - A custom Predicate that will be used to filter attribute names. The default predicate looks like this:<pre>
Predicate&lt;? super SchemaAttribute&gt; attributeNameFilter = a -&gt; {
						return !a.getName().equals("DB_ID")
							&& !a.getName().equals("dateTime")
							&& !a.getName().equals("modified")
							&& !a.getName().equals("created");
					};
	 </pre>
	 * @param checkReferrers - Should referring instances also be checked? If <b>true</b>, then referring attributes will <em>also</em> be checked for differences.
	 * They will be followed to the same recursion depth as regular attributes. Using this with a high maxRecursionDepth could lead to a very long execution time. Be careful!!
	 * You might also want to use a custom predicate to filter out attributes that could lead to a cyclical difference check.
	 * @return The number of differences between the two instances.
	 * A single-valued attribute that differs will count as 1 diff.
	 * If the instances have different schema classes, that will count as 1 diff.
	 * If a multi-valued attribute has a different number of elements between the two intances, 
	 * that will count as 1 diff and the elements will NOT be compared.
	 */
	public static int compareInstances(GKInstance inst1, GKInstance inst2, StringBuilder sb, int maxRecursionDepth, Predicate<? super SchemaAttribute> customAttributeNameFilter, boolean checkReferrers)
	{
		return compareInstances(inst1, inst2, sb, 0, 0, maxRecursionDepth, customAttributeNameFilter, checkReferrers);
	}
	
	/**
	 * Recursively compares two GKInstances.
	 * @param inst1 - the first instance.
	 * @param inst2 - the second instance.
	 * @param sb - a StringBuilder that will contain a detailed report of differences.
	 * @param diffCount - The number of differences so far. Should start at 0.
	 * @param recursionDepth - The depth of the recursion so far. Should start at 0.
	 * @param customAttributeNameFilter - A custom Predicate that will be used to filter attribute names. The default predicate looks like this:<pre>
Predicate&lt;? super SchemaAttribute&gt; attributeNameFilter = a -&gt; {
						return !a.getName().equals("DB_ID")
							&& !a.getName().equals("dateTime")
							&& !a.getName().equals("modified")
							&& !a.getName().equals("created");
					};
	 </pre>
	 * @param checkReferrers - Should referring instances also be checked? If <b>true</b>, then referring attributes will <em>also</em> be checked for differences.
	 * They will be followed to the same recursion depth as regular attributes. Using this with a high maxRecursionDepth could lead to a very long execution time. Be careful!!
	 * You might also want to use a custom predicate to filter out attributes that could lead to a cyclical difference check.
	 * @return The number of differences between the two instances.
	 * A single-valued attribute that differs will count as 1 diff.
	 * If the instances have different schema classes, that will count as 1 diff.
	 * If a multi-valued attribute has a different number of elements between the two intances, 
	 * that will count as 1 diff and the elements will NOT be compared.
	 */
	private static int compareInstances(GKInstance inst1, GKInstance inst2, StringBuilder sb, int diffCount, int recursionDepth, int maxRecursionDepth, Predicate<? super SchemaAttribute> customAttributeNameFilter, boolean checkReferrers)
	{
		int count = diffCount;
		
		if (inst1 != null && inst2 != null)
		{
			SchemaClass class1 = inst1.getSchemClass();
			SchemaClass class2 = inst2.getSchemClass();
			
			if (!class1.getName().equals("InstanceEdit") && !class2.getName().equals("InstanceEdit"))
			{
				String[] indentArray = new String[recursionDepth * 2];
				Arrays.fill(indentArray, " ");
				String indentString = String.join("", indentArray);
				
				if (!class1.getName().equals(class2.getName()))
				{
					sb.append(indentString + "Schema classes don't match, so instances can't be compared! Instance 1 is a " + class1.getName() + " and Instance 2 is a " + class2.getName()).append("\n");
					return 1;
				}
				else
				{
					/*
					 * Used for filtering out attributes that you don't want to compare because they will
					 * probably create too much noise if you do.
					 */
					Predicate<? super SchemaAttribute> attributeNameFilter;
					if (customAttributeNameFilter!=null)
					{
						attributeNameFilter = customAttributeNameFilter;
					}
					else
					{
						attributeNameFilter = a -> {
							return !a.getName().equals(ReactomeJavaConstants.DB_ID)
								&& !a.getName().equals(ReactomeJavaConstants.dateTime)
								&& !a.getName().equals(ReactomeJavaConstants.modified)
								&& !a.getName().equals(ReactomeJavaConstants.created);
						};
					}
					
					@SuppressWarnings("unchecked")
					List<SchemaAttribute> regularAttributes = ( (Collection<SchemaAttribute>) class1.getAttributes()).stream().filter(attributeNameFilter).collect(Collectors.toList());
					@SuppressWarnings("unchecked")
					List<SchemaAttribute> referrerAttributes = ( (Collection<SchemaAttribute>) class1.getReferers()).stream().filter(attributeNameFilter).collect(Collectors.toList());
					List<SchemaAttribute> allAttributes = new ArrayList<SchemaAttribute>(regularAttributes.size() + referrerAttributes.size());
					
					allAttributes.addAll(regularAttributes);
					if (checkReferrers)
					{
						// Add the referrer attributes to the list of all attributes, if the user wants to check referrer attributes.
						allAttributes.addAll(referrerAttributes);
					}
					
					for (SchemaAttribute attrib : allAttributes)
					{
						String attributeQualifier = "";
						// getValuesFunction will refer to whichever function needs to be called,
						// depending on whether or not the current attribute is a regular attribute or a referrer attribute.
						BiFunction<String, GKInstance, List<?>> getValuesFunction;
						// If the current attribute is in the "regular" attributes list, then the function that needs
						// to get called is getAttributeValuesList() so we will
						// create a lambda for that and assign it to getValuesFunction for later use.
						if (regularAttributes.contains(attrib))
						{
							getValuesFunction = (s, i) ->
							{
								try
								{
									return (List<GKInstance>) i.getAttributeValuesList(s);
								}
								catch (Exception e)
								{
									e.printStackTrace();
								}
								return null;
							} ;
						}
						else
						{
							// In this case, the attribute was not in the "regular" attributes so it must be a referrer.
							// The function that will need to be called is getReferrers()
							attributeQualifier = " referrer";
							getValuesFunction = (s, i) ->
							{
								try
								{
									return (List<GKInstance>) i.getReferers(s);
								}
								catch (Exception e)
								{
									e.printStackTrace();
								}
								return null;
							} ;
						}
	
						// Deal with attributes that return GKInstance objects.
						if (attrib.getType().equals(GKInstance.class) || attrib.getType().equals(Instance.class))
						{
							List<GKInstance> values1 = new ArrayList<GKInstance>();
							values1 = (List<GKInstance>) getValuesFunction.apply(attrib.getName(), inst1);
							
							List<GKInstance> values2 = new ArrayList<GKInstance>();
							values2 = (List<GKInstance>) getValuesFunction.apply(attrib.getName(), inst2);
	
							if (values1 != null && values2 != null)
							{
								// Make sure the lists are sorted so that you are always comparing objects in the same sequence: I don't think the database adaptor applies 
								// any explicit order to Instances that don't have a rank/order attribute.
								InstanceUtilities.sortInstances(values1);
								InstanceUtilities.sortInstances(values2);
								if (values1.size() == values2.size())
								{
									// compare each item in one list to the corresponding item in the other list - the MySQLAdaptor seems to preserve sequence of items in lists properly.
									for (int i = 0 ; i < values1.size() ; i++)
									{
										// Recurse, if max depth has not yet been reacehed.
										if (recursionDepth < maxRecursionDepth)
										{
											sb.append(indentString).append(" Recursing on ").append(attrib.getName()).append(" attribute...\n"); 
											count = compareInstances(values1.get(i), values2.get(i), sb, count, recursionDepth + 1, maxRecursionDepth, attributeNameFilter, checkReferrers);
										}
									}
								}
								else
								{
									// no point comparing/recursing if the lists don't even have the same size. Just write a message about that.
									sb.append(indentString + "Count mismatch for multi-valued"+attributeQualifier+" attribute \"" + attrib.getName() + "\" Instance 1 (\""+inst1.toString()+"\") has " + values1.size() + " elements but Instance 2 (\""+inst2.toString()+"\") has " + values2.size() + " elements.\n");
									count ++;
								}
							}
						}
						// Deal with attributes that return "simple" things (Strings, numbers, etc..., arrays of Strings/numbers/etc...)
						else
						{	
							List<Object> values1 = new ArrayList<Object>();
							values1 = (List<Object>) getValuesFunction.apply(attrib.getName(), inst1);
							
							List<Object> values2 = new ArrayList<Object>();
							values2 = (List<Object>) getValuesFunction.apply(attrib.getName(), inst2);
							
							if (values1 != null && values2 != null)
							{
								if (values1.size() == values2.size())
								{
									for (int i = 0 ; i < values1.size() ; i++)
									{
										// compare each item in one list to the corresponding item in the other list - the MySQLAdaptor seems to preserve sequence of items in lists properly.
										if (!values1.get(i).equals(values2.get(i)))
										{
											sb.append(indentString + "Mismatch on"+attributeQualifier+" attribute \""+attrib.getName()+"\"\n"+indentString+"Instance 1 (\""+inst1.toString()+"\") has value:\t"+values1.get(i) + "\n"+indentString+"Instance 2 (\""+inst2.toString()+"\") has value:\t"+values2.get(i)).append("\n");
											count++;
										}
									}
								}
								else
								{
									// no point comparing if the lists don't even have the same size. Just write a message about that.
									sb.append(indentString + "Count mismatch for multi-valued"+attributeQualifier+" attribute \"" + attrib.getName() + "\" Instance 1 (\""+inst1.toString()+"\") has " + values1.size() + " elements but Instance 2 (\""+inst2.toString()+"\") has " + values2.size() + " elements.\n");
									count ++;
								}
							}
						}
					}
				}
			}
		}
		return count;
	}
}
