package org.reactome.util.compare;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.Instance;
import org.gk.schema.InvalidAttributeException;
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
		return compareInstances(inst1, inst2, sb, 5);
	}
	
	/**
	 * Compares two GKInstances.
	 * @param inst1 - the first instance.
	 * @param inst2 - the second instance.
	 * @param sb - a StringBuilder that will contain a detailed report of differences.
	 * @param maxRecursionDepth - the maximum depth of recursion that will be allowed. Normally a depth of 2 or 3 is probably sufficient.
	 * @return The number of differences between the two instances.
	 * A single-valued attribute that differs will count as 1 diff.
	 * If the instances have different schema classes, that will count as 1 diff.
	 * If a multi-valued attribute has a different number of elements between the two intances, 
	 * that will count as 1 diff and the elements will NOT be compared.
	 */
	public static int compareInstances(GKInstance inst1, GKInstance inst2, StringBuilder sb, int maxRecursionDepth)
	{
		return compareInstances(inst1, inst2, sb, 0, 0, maxRecursionDepth, null);
	}
	
	/**
	 * Compares two GKInstances.
	 * @param inst1 - the first instance.
	 * @param inst2 - the second instance.
	 * @param sb - a StringBuilder that will contain a detailed report of differences.
	 * @param maxRecursionDepth - the maximum depth of recursion that will be allowed. Normally a depth of 2 or 3 is probably sufficient.
	 * @return The number of differences between the two instances.
	 * A single-valued attribute that differs will count as 1 diff.
	 * If the instances have different schema classes, that will count as 1 diff.
	 * If a multi-valued attribute has a different number of elements between the two intances, 
	 * that will count as 1 diff and the elements will NOT be compared.
	 */
	public static int compareInstances(GKInstance inst1, GKInstance inst2, StringBuilder sb, int maxRecursionDepth, Predicate<? super SchemaAttribute> customAttributeNameFilter)
	{
		return compareInstances(inst1, inst2, sb, 0, 0, maxRecursionDepth, customAttributeNameFilter);
	}
	
	/**
	 * Recursively compares two GKInstances.
	 * @param inst1 - the first instance.
	 * @param inst2 - the second instance.
	 * @param sb - a StringBuilder that will contain a detailed report of differences.
	 * @param diffCount - The number of differences so far. Should start at 0.
	 * @param recursionDepth - The depth of the recursion so far. Should start at 0.
	 * @return The number of differences between the two instances.
	 * A single-valued attribute that differs will count as 1 diff.
	 * If the instances have different schema classes, that will count as 1 diff.
	 * If a multi-valued attribute has a different number of elements between the two intances, 
	 * that will count as 1 diff and the elements will NOT be compared.
	 */
	private static int compareInstances(GKInstance inst1, GKInstance inst2, StringBuilder sb, int diffCount, int recursionDepth, int maxRecursionDepth, Predicate<? super SchemaAttribute> customAttributeNameFilter)
	{
		int count = diffCount;
		SchemaClass class1 = inst1.getSchemClass();
		SchemaClass class2 = inst2.getSchemClass();
		
		if (!class1.getName().equals("InstanceEdit") && !class2.getName().equals("InstanceEdit"))
		{
			if (!class1.getName().equals(class2.getName()))
			{
				sb.append("Schema classes don't match, so instances can't be compared! Instance 1 is a " + class1.getName() + " and Instance 2 is a " + class2.getName()).append("\n");
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
				List<SchemaAttribute> attributes = ( (Collection<SchemaAttribute>) class1.getAttributes()).stream().filter(attributeNameFilter).collect(Collectors.toList());
				
				for (SchemaAttribute attrib : attributes)
				{
					if (attrib.getType().equals(GKInstance.class) || attrib.getType().equals(Instance.class))
					{
						List<GKInstance> values1 = new ArrayList<GKInstance>();
						try
						{
							values1 = (List<GKInstance>) inst1.getAttributeValuesList(attrib.getName());
						}
						catch (InvalidAttributeException e)
						{
							e.printStackTrace();
						}
						catch (Exception e)
						{
							e.printStackTrace();
						}
						
						List<GKInstance> values2 = new ArrayList<GKInstance>();
						try
						{
							values2 = (List<GKInstance>) inst2.getAttributeValuesList(attrib.getName());
						}
						catch (InvalidAttributeException e)
						{
							e.printStackTrace();
						}
						catch (Exception e)
						{
							e.printStackTrace();
						}
						
						if (!values1.isEmpty() && !values2.isEmpty())
						{
							if (values1.size() == values2.size())
							{
								for (int i = 0 ; i < values1.size() ; i++)
								{
									if (recursionDepth < maxRecursionDepth)
									{
										count = compareInstances(values1.get(i), values2.get(i), sb, count, recursionDepth + 1, maxRecursionDepth, attributeNameFilter);
									}
								}
							}
							else
							{
								sb.append("Count mismatch for multi-valued attribute \"" + attrib.getName() + "\" Instance 1 has " + values1.size() + " elements but Instance 2 has " + values2.size() + " elements.\n");
								count ++;
							}
						}
						
					}
					else
					{	
						List<Object> values1 = new ArrayList<Object>();
						try
						{
							values1 = inst1.getAttributeValuesList(attrib.getName());
						}
						catch (InvalidAttributeException e)
						{
							e.printStackTrace();
						}
						catch (Exception e)
						{
							e.printStackTrace();
						}
						
						List<Object> values2 = new ArrayList<Object>();
						try
						{
							values2 = inst2.getAttributeValuesList(attrib.getName());
						}
						catch (InvalidAttributeException e)
						{
							e.printStackTrace();
						}
						catch (Exception e)
						{
							e.printStackTrace();
						}
						

						if (!values1.isEmpty() && !values2.isEmpty())
						{
							if (values1.size() == values2.size())
							{
								for (int i = 0 ; i < values1.size() ; i++)
								{
									if (!values1.get(i).equals(values2.get(i)))
									{
										sb.append("Mismatch on attribute \""+attrib.getName()+"\"\nInstance 1 has value:\t"+values1.get(i) + "\nInstance 2 has value:\t"+values2.get(i)).append("\n");
										count++;
									}
								}
							}
							else
							{
								sb.append("Count mismatch for multi-valued attribute \"" + attrib.getName() + "\"Instance 1 has " + values1.size() + " elements but Instance 2 has " + values2.size() + " elements.\n");
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
