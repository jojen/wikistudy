/*   JPA cloner project.
 *   
 *   Copyright (C) 2013 Miroslav Nociar
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
package org.jojen.wikistudy.util.jpacloner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;

import org.jojen.wikistudy.util.jpacloner.graphs.EntityExplorer;
import org.jojen.wikistudy.util.jpacloner.JpaIntrospector.JpaClassInfo;
import org.jojen.wikistudy.util.jpacloner.graphs.EntityExplorer;
import org.jojen.wikistudy.util.jpacloner.graphs.GraphExplorer;
import org.jojen.wikistudy.util.jpacloner.graphs.PropertyFilter;


/**
 * JpaCloner provides cloning of JPA entity subgraphs. There are three options for cloning:
 * <ol>
 * <li> 
 * Cloning by string patterns which define <b>entity relations</b>. For description of patterns see the {@link GraphExplorer}.<br/>
 * <b>All basic properties</b> (non-relation columns) of entities are cloned by default in this case. Usage example:
 * <pre>
 * Company clonedCompany = JpaCloner.clone(company, "department+.(boss|employees).address.(country|city|street)");</pre>
 * </li>
 * <li>
 * Cloning by {@link PropertyFilter} gives full control over the cloning process. <br/>
 * <b>Entity relations</b> and <b>basic properties</b> for cloning are defined by the filter implementation. Usage example:
 * <pre>
 * Company clonedCompany = JpaCloner.clone(company, new MyPropertyFilter());</pre>
 * </li>
 * <li>
 * Cloning by string patterns and {@link PropertyFilter}. <b>Entity relations</b> for cloning are defined by the string patterns.
 * <b>Basic properties</b> for cloning are defined by the filter implementation. Usage example:
 * <pre>
 * PropertyFilter filter = new PropertyFilter() {
 *     public boolean isCloned(Object entity, String property) {
 *         // do not clone primary keys
 *         return !"id".equals(property);
 *     }
 * } 
 * Company clonedCompany = JpaCloner.clone(company, filter, "department+.(boss|employees).address.(country|city|street)");</pre>
 * </li>
 * </ol>
 * Requirements:
 * <ul>
 * <li>JPA entities must <b>correctly</b> implement the {@link Object#equals(Object obj)} 
 * method and the {@link Object#hashCode()} method!</li>
 * <li>JPA entities must use <b>field access</b>, not property access. 
 * Each field must have a corresponding <b>getter</b>.</li>
 * <li>Cloned entities will be instantiated as <b>raw classes</b>, not Hibernate proxy classes.
 * Raw classes means classes annotated by {@link javax.persistence.Entity} or {@link javax.persistence.Embeddable}.</li>
 * <li>Cloned entities will have all basic properties (i.e. columns) populated by default. 
 * Advanced control over the basic property cloning is supported via the {@link PropertyFilter}.</li>
 * <li>Relations to neighboring entities will be populated <b>only</b> if specified by the string patterns or the {@link PropertyFilter}, <code>null</code> otherwise.</li>
 * <li>{@link javax.persistence.OneToMany#mappedBy()} and {@link javax.persistence.OneToOne#mappedBy()} attributes are handled automatically</li>
 * <li>Cloned collections and maps will be the standard java.util classes:
 * <table>
 * <tr><th>Original</th><th></th><th>Clone</th></tr>
 * <tr><td>{@link java.util.List}</td><td>-&gt;</td><td>{@link java.util.ArrayList}</td></tr>
 * <tr><td>{@link java.util.SortedSet}</td><td>-&gt;</td><td>{@link java.util.TreeSet}</td></tr>
 * <tr><td>{@link java.util.Set}</td><td>-&gt;</td><td>{@link java.util.HashSet}</td></tr>
 * <tr><td>{@link java.util.SortedMap}</td><td>-&gt;</td><td>{@link java.util.TreeMap}</td></tr>
 * <tr><td>{@link java.util.Map}</td><td>-&gt;</td><td>{@link java.util.HashMap}</td></tr>
 * </table>
 * </li>
 * <li>Cloning of {@link java.util.Map} supports navigation via "key" and "value" properties e.g. "my.map.(key.a.b.c|value.x.y.z)"</li>
 * <li>A JpaCloner instance is NOT thread safe; prefer the usage of static clone(...) methods, which are.</li>
 * </ul>
 * Please note that the cloning has also a side effect regarding the lazy loading. 
 * All entities which will be cloned must be fetched from the DB. It is advisable
 * (but not required) to perform the cloning inside a <b>transaction scope</b>.
 * 
 * @author Miroslav Nociar
 */
public class JpaCloner implements EntityExplorer {

	private final Map<Object, Object> originalToClone = new HashMap<Object, Object>();
	private final Map<Object, Map<String, Collection<Object>>> exploredCache = new HashMap<Object, Map<String, Collection<Object>>>();
	
	private final PropertyFilter propertyFilter;
	
	private static final PropertyFilter defaultPropertyFilter = new PropertyFilter() {
		@Override
		public boolean test(Object entity, String property) {
			return true;
		}
	};
	
	public JpaCloner() {
		this(defaultPropertyFilter);
	}
	
	public JpaCloner(PropertyFilter propertyFilter) {
		this.propertyFilter = propertyFilter;
	}
	
	@Override
	public Collection<Object> explore(Object original, String property) {
		if (original == null) {
			return null;
		}
		Map<String, Collection<Object>> propertyToExplored = exploredCache.get(original);
		if (propertyToExplored  == null) {
			propertyToExplored = new HashMap<String, Collection<Object>>();
			exploredCache.put(original, propertyToExplored);
		}
		Collection<Object> explored = propertyToExplored.get(property);
		if (explored == null) {
			explored = exploreAndClone(original, property);
			propertyToExplored.put(property, explored);
		}
		return explored;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Collection<Object> exploreAndClone(Object original, String property) {
		if (original instanceof Entry) {
			Entry entry = (Entry) original;
			// handle Map.Entry#getKey() and Map.Entry#getValue()
			if ("key".equals(property)) {
				return Collections.singleton(entry.getKey());
			} else if ("value".equals(property)) {
				return Collections.singleton(entry.getValue());
			} else {
				throw new IllegalArgumentException("Map.Entry does not have property: " + property);
			}
		}
		
		JpaClassInfo info = JpaIntrospector.getClassInfo(original);
		if (info == null || !info.getRelations().contains(property)) {
			return null;
		}
		
		Object clone = getClone(original);
		Object value = JpaIntrospector.getProperty(original, property);

		if (value == null) {
			return null;
		}

		Object clonedValue;
		Collection explored;
		
		if (value instanceof Collection) {
			// Collection property
			explored = (Collection) value;
			Collection clonedCollection;
			if (explored instanceof SortedSet) {
				// create a tree set with the same comparator (may be null)
				clonedCollection = new TreeSet(((SortedSet) explored).comparator());
			} else if (explored instanceof Set) {
				// create a hash set
				clonedCollection = new HashSet();
			} else if (explored instanceof List) {
				// create an array list
				clonedCollection = new ArrayList(explored.size());
			} else {
				throw new IllegalArgumentException("Unsupported collection class: " + explored.getClass());
			}
			for (Object o : explored) {
				clonedCollection.add(getClone(o));
			}
			clonedValue = clonedCollection;
			handleMappedBy(explored, info, property);
		} else if (value instanceof Map) {
			// Map property
			Map map = (Map) value;
			explored = map.entrySet();
			Map clonedMap;
			if (value instanceof SortedMap) {
				clonedMap = new TreeMap(((SortedMap) value).comparator());
			} else {
				clonedMap = new HashMap();
			}
			for (Object e : explored) {
				Entry entry = (Entry) e;
				clonedMap.put(getClone(entry.getKey()), getClone(entry.getValue()));
			}
			clonedValue = clonedMap;
			handleMappedBy(map.values(), info, property);
		} else {
			// singular property
			explored = Collections.singleton(value);
			clonedValue = getClone(value);
			handleMappedBy(explored, info, property);
		}
		
		JpaIntrospector.setProperty(clone, property, clonedValue);

		return explored;
	}
	
	@SuppressWarnings("rawtypes")
	private void handleMappedBy(Collection explored, JpaClassInfo info, String property) {
		String[] mappedBy = info.getMappedBy(property);
		if (mappedBy != null) {
			for (Object e : explored) {
				handleMappedBy(e, mappedBy, 0);
			}
		}
	}

	private void handleMappedBy(Object o, String[] mappedBy, int idx) {
		Collection<Object> explored = explore(o, mappedBy[idx]);
		idx++;
		if (explored != null && idx < mappedBy.length) {
			for (Object e : explored) {
				handleMappedBy(e, mappedBy, idx);
			}
		}
	}

	public Object getClone(Object original) {
		if (original == null) {
			return null;
		}
		// check the cache first
		Object clone = originalToClone.get(original);
		if (clone != null) {
			return clone;
		}
		JpaClassInfo classInfo = JpaIntrospector.getClassInfo(original);
		if (classInfo == null) {
			// not a JPA class, return the original object
			return original;
		}
		try {
			clone = classInfo.getConstructor().newInstance();
		} catch (Exception e) {
			throw new IllegalStateException("Unable to clone: " + original, e);
		}
		// copy columns
		copyColumns(original, clone, classInfo, propertyFilter);
		// put in the cache
		originalToClone.put(original, clone);
		return clone;
	}

	public Map<Object, Object> getOriginalToClone() {
		return originalToClone;
	}
	
	private static final Map<String, GraphExplorer> patternToExplorer = new ConcurrentHashMap<String, GraphExplorer>();
	
	private static GraphExplorer getExplorer(String pattern) {
		GraphExplorer explorer = patternToExplorer.get(pattern);
		if (explorer == null) {
			explorer = new GraphExplorer(pattern);
			patternToExplorer.put(pattern, explorer);
		}
		return explorer;
	}

	@SuppressWarnings("unchecked")
	private static <T> T clone(T root, JpaCloner jpaCloner, String... patterns) {
		if (patterns != null) {
			for (String pattern : patterns) {
				GraphExplorer explorer = getExplorer(pattern);
				explorer.explore(root, jpaCloner);
			}
		}
		return (T) jpaCloner.getClone(root);
	}

	/**
	 * Private helper method for collection cloning. 
	 */
	private static <T> void cloneCollection(Collection<T> originalCollection, Collection<T> clonedCollection, PropertyFilter propertyFilter, String... patterns) {
		JpaCloner jpaCloner = new JpaCloner(propertyFilter);
		for (T root : originalCollection) {
			clonedCollection.add(clone(root, jpaCloner, patterns));
		}
	}

	/**
	 * Clones the passed JPA entity. The property filter controls the cloning of <b>basic properties</b>. 
	 * The cloned relations are specified by string patters. For description of patterns see the {@link GraphExplorer}.
	 */
	public static <T> T clone(T root, PropertyFilter propertyFilter, String... patterns) {
		return clone(root, new JpaCloner(propertyFilter), patterns);
	}

	/**
	 * Clones the list of JPA entities. The property filter controls the cloning of <b>basic properties</b>. 
	 * The cloned relations are specified by string patters. For description of patterns see the {@link GraphExplorer}.
	 */
	public static <T> List<T> clone(List<T> list, PropertyFilter propertyFilter, String... patterns) {
		List<T> clonedList = new ArrayList<T>(list.size());
		cloneCollection(list, clonedList, propertyFilter, patterns);
		return clonedList;
	}

	/**
	 * Clones the set of JPA entities. The property filter controls the cloning of <b>basic properties</b>. 
	 * The cloned relations are specified by string patters. For description of patterns see the {@link GraphExplorer}.
	 */
	public static <T> Set<T> clone(Set<T> set, PropertyFilter propertyFilter, String... patterns) {
		Set<T> clonedSet = new HashSet<T>();
		cloneCollection(set, clonedSet, propertyFilter, patterns);
		return clonedSet;
	}

	/**
	 * Clones the passed JPA entity. Each entity has <b>all basic properties</b> cloned. 
	 * The cloned relations are specified by string patters. For description of patterns see the {@link GraphExplorer}.
	 */
	public static <T> T clone(T root, String... patterns) {
		return clone(root, defaultPropertyFilter, patterns);
	}

	/**
	 * Clones the list of JPA entities. Each entity has <b>all basic properties</b> cloned. 
	 * The cloned relations are specified by string patters. For description of patterns see the {@link GraphExplorer}.
	 */
	public static <T> List<T> clone(List<T> list, String... patterns) {
		return clone(list, defaultPropertyFilter, patterns);
	}

	/**
	 * Clones the set of JPA entities. Each entity has <b>all basic properties</b> cloned. 
	 * The cloned relations are specified by string patters. For description of patterns see the {@link GraphExplorer}.
	 */
	public static <T> Set<T> clone(Set<T> set, String... patterns) {
		return clone(set, defaultPropertyFilter, patterns);
	}

	@SuppressWarnings("unchecked")
	private static <T> T clone(T root, JpaCloner jpaCloner, Set<Object> exploredEntities) {
		GraphExplorer.explore(root, exploredEntities, jpaCloner, JpaIntrospector.INSTANCE, jpaCloner.propertyFilter);
		return (T) jpaCloner.getClone(root);
	}

	/**
	 * Private helper method for collection cloning. 
	 */
	private static <T> void cloneCollection(Collection<T> originalCollection, Collection<T> clonedCollection, PropertyFilter propertyFilter) {
		JpaCloner jpaCloner = new JpaCloner(propertyFilter);
		Set<Object> exploredEntities = new HashSet<Object>();
		for (T root : originalCollection) {
			clonedCollection.add(clone(root, jpaCloner, exploredEntities));
		}
	}
	
	/**
	 * Clones the passed JPA entity by filter. The property filter controls
	 * the cloning of basic properties and relations.
	 */
	public static <T> T clone(T root, PropertyFilter propertyFilter) {
		return clone(root, new JpaCloner(propertyFilter), new HashSet<Object>()); 
	}
	
	/**
	 * Clones the list of JPA entities by filter. The property filter controls
	 * the cloning of basic properties and relations.
	 */
	public static <T> List<T> clone(List<T> list, PropertyFilter propertyFilter) {
		List<T> clonedList = new ArrayList<T>(list.size());
		cloneCollection(list, clonedList, propertyFilter);
		return clonedList;
	}

	/**
	 * Clones the set of JPA entities by filter. The property filter controls
	 * the cloning of basic properties and relations.
	 */
	public static <T> Set<T> clone(Set<T> set, PropertyFilter propertyFilter) {
		Set<T> clonedSet = new HashSet<T>();
		cloneCollection(set, clonedSet, propertyFilter);
		return clonedSet;
	}

	/**
	 * Copy all columns from o1 to o2.
	 */
	private static void copyColumns(Object o1, Object o2, JpaClassInfo classInfo, PropertyFilter propertyFilter) {
		for (String property : classInfo.getColumns()) {
			if (propertyFilter.test(o1, property)) {
				Object value = JpaIntrospector.getProperty(o1, property);
				JpaIntrospector.setProperty(o2, property, value);
			}
		}
	}
	
	public static <T, X extends T> void copy(T o1, X o2) {
		copy(o1, o1, defaultPropertyFilter);
	}
	
	public static <T, X extends T> void copy(T o1, X o2, PropertyFilter propertyFilter) {
		JpaClassInfo classInfo = JpaIntrospector.getClassInfo(o1);
		if (classInfo == null) {
			return;
		}
		copyColumns(o1, o2, classInfo, propertyFilter);
	}
}
