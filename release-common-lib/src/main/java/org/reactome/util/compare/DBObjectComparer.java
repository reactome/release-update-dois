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
						return !a.getName().equals("DB_ID")
							&& !a.getName().equals("dateTime")
							&& !a.getName().equals("modified")
							&& !a.getName().equals("created");
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
					allAttributes.addAll(referrerAttributes);
				}
				
				for (SchemaAttribute attrib : allAttributes)
				{
					String attributeQualifier = "";
					BiFunction<String, GKInstance, List<?>> getValuesFunction;
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

					if (attrib.getType().equals(GKInstance.class) || attrib.getType().equals(Instance.class))
					{
						List<GKInstance> values1 = new ArrayList<GKInstance>();
						values1 = (List<GKInstance>) getValuesFunction.apply(attrib.getName(), inst1);
						
						List<GKInstance> values2 = new ArrayList<GKInstance>();
						values2 = (List<GKInstance>) getValuesFunction.apply(attrib.getName(), inst2);

						if (values1 != null && values2 != null)
						{
							if (values1.size() == values2.size())
							{
								for (int i = 0 ; i < values1.size() ; i++)
								{
									if (recursionDepth < maxRecursionDepth)
									{
										sb.append(indentString).append(" Recursing on ").append(attrib.getName()).append("...\n"); 
										count = compareInstances(values1.get(i), values2.get(i), sb, count, recursionDepth + 1, maxRecursionDepth, attributeNameFilter, checkReferrers);
									}
								}
							}
							else
							{
								sb.append(indentString + "Count mismatch for multi-valued"+attributeQualifier+" attribute \"" + attrib.getName() + "\" Instance 1 (\""+inst1.toString()+"\") has " + values1.size() + " elements but Instance 2 (\""+inst2.toString()+"\") has " + values2.size() + " elements.\n");
								count ++;
							}
						}
					}
					else
					{	
						List<Object> values1 = new ArrayList<Object>();
						//values1 = inst1.getAttributeValuesList(attrib.getName());
						values1 = (List<Object>) getValuesFunction.apply(attrib.getName(), inst1);
						
						List<Object> values2 = new ArrayList<Object>();
						//values2 = inst2.getAttributeValuesList(attrib.getName());
						values2 = (List<Object>) getValuesFunction.apply(attrib.getName(), inst2);
						
						if (values1 != null && values2 != null)
						{
							if (values1.size() == values2.size())
							{
								for (int i = 0 ; i < values1.size() ; i++)
								{
									if (!values1.get(i).equals(values2.get(i)))
									{
										sb.append(indentString + "Mismatch on"+attributeQualifier+" attribute \""+attrib.getName()+"\"\n"+indentString+"Instance 1 (\""+inst1.toString()+"\") has value:\t"+values1.get(i) + "\n"+indentString+"Instance 2 (\""+inst2.toString()+"\") has value:\t"+values2.get(i)).append("\n");
										count++;
									}
								}
							}
							else
							{
								sb.append(indentString + "Count mismatch for multi-valued"+attributeQualifier+" attribute \"" + attrib.getName() + "\" Instance 1 (\""+inst1.toString()+"\") has " + values1.size() + " elements but Instance 2 (\""+inst2.toString()+"\") has " + values2.size() + " elements.\n");
								count ++;
							}

						}
					}
				}
			}
		}
		return count;
	}
}
