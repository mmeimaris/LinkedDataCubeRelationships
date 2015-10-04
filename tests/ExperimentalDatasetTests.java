package tests;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import models.Dimension;
import models.DimensionFactory;
import models.Observation;
import models.TreeHierarchy;
import net.sf.javaml.clustering.Clusterer;
import net.sf.javaml.clustering.KMeans;
import net.sf.javaml.core.Dataset;
import net.sf.javaml.core.DefaultDataset;
import net.sf.javaml.core.Instance;
import net.sf.javaml.core.SparseInstance;
import net.sf.javaml.sampling.Sampling;
import net.sf.javaml.tools.weka.ToWekaUtils;
import virtuoso.jena.driver.VirtGraph;
import virtuoso.jena.driver.VirtuosoQueryExecution;
import virtuoso.jena.driver.VirtuosoQueryExecutionFactory;
import weka.clusterers.Canopy;
import weka.clusterers.ClusterEvaluation;
import weka.core.Instances;
import app.DataConnection;
import au.com.bytecode.opencsv.CSVWriter;
import be.abeel.util.Pair;

import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;

public class ExperimentalDatasetTests {

	/**
	 * @param args
	 */
	static HashSet<String> graphs = new HashSet<String>();
	static String connectionString = "jdbc:virtuoso://83.212.121.252:1111/autoReconnect=true/charset=UTF-8/log_enable=2";
	static String prefix = "http://imis.athena-innovation.gr/def#";
	static String codePrefix ="http://imis.athena-innovation.gr/code#";
	static ArrayList<String> featureList;
	static HashMap<String, Integer> levelMap;
	static HashMap<String, Integer> valueIndexMap;
	//static HashMap<String, Integer> levelMap = new HashMap<String, Integer>();
	static TreeHierarchy hierarchy;
	static HashMap<String, Observation> obs;
	static Set<Dimension> dims; 
	static HashMap<Observation, ArrayList<String>> cubeBuckets;
	static long computed, computedPartial, nulls, start;
	static int nextIndex = 0;
	static int numberOfClusters = 4;
	
	public static void main(String[] args) {
		
		init();
		//runTest(args);
		runTestClustering(args);
		/*for(int i = 0; i <10; i++){
			init();
			System.out.println("Elapsed time: " + runTest(args));
		}*/
		
	}
	
	public static void init(){
		computed = 0;
		nulls = 0;
		featureList = new ArrayList<String>();
		levelMap = new HashMap<String, Integer>();
		valueIndexMap = new HashMap<String, Integer>(); 
	}
	
	public static long runTest(String[] args){
		
		hierarchy = new TreeHierarchy(new File(args[0]));
		if(args.length>1) numberOfClusters = Integer.parseInt(args[1]);
		start = System.nanoTime();    						
		populateGraphs();
		createLevelMap();
		createHierarchyMap();
		createHashMap();				
		long elapsedTime = System.nanoTime() - start;
		//System.out.println("Elapsed time: " + elapsedTime);
		//System.out.println("Nulls: "+nulls);
		return elapsedTime;
	}
	
	public static long runTestClustering(String[] args){
		
		hierarchy = new TreeHierarchy(new File(args[0]));
		start = System.nanoTime();    						
		populateGraphs();
		createLevelMap();
		createHierarchyMap();
		System.out.println("-----");
		//System.out.println(hierarchy.getNode("http://linked-statistics.gr/resource/admin-division/2011/region/471").getParents());
		//createHashMap();
		createDataset();
		long elapsedTime = System.nanoTime() - start;
		//System.out.println("Elapsed time: " + elapsedTime);
		//System.out.println("Nulls: "+nulls);
		return elapsedTime;
	}
	
public static long runTestNaive(String[] args){
		
		hierarchy = new TreeHierarchy(new File(args[0]));
		start = System.nanoTime();    						
		populateGraphs();
		createLevelMap();
		createHierarchyMap();
		createHashMapNaive();				
		long elapsedTime = System.nanoTime() - start;
		//System.out.println("Elapsed time: " + elapsedTime);
		//System.out.println("Nulls: "+nulls);
		return elapsedTime;
	}
	
	public static void createLevelMap(){		
		VirtGraph graph = DataConnection.getConnection();
		String query = " SELECT DISTINCT ?value ?level" + 					
					" FROM <codelists.age> " +
					" FROM <codelists.sex> " +
					" FROM <codelists.location> " +
					" FROM <codelists.admin.divisions> " +
					" FROM <codelists.sameas> " +
					" WHERE {" + 					
					"   {?value imis1:level ?level }" + 
					"UNION " +
					"   {?dummy owl:sameAs ?value . ?value imis1:level ?level }" + 
					 "}";
		System.out.println(query);
		VirtuosoQueryExecution vqe = VirtuosoQueryExecutionFactory.create (query, graph);
		ResultSet results = vqe.execSelect();			
		while(results.hasNext()){				
			QuerySolution rs = results.next();
			levelMap.put(rs.get("value").toString(), rs.getLiteral("level").getInt());
			valueIndexMap.put(rs.get("value").toString(), getNextInt());
		}
		vqe.close();				
		graph.close();
		//System.out.println(levelMap.size());
	}
	
	public static int getNextInt(){
		return nextIndex++;
	}
	
	public static void createHashMap(){
		VirtGraph graph = DataConnection.getConnection();
		String query;
		obs = new HashMap<String, Observation>();
		HashSet<Observation> cubes = new HashSet<Observation>();
		cubeBuckets = new HashMap<Observation, ArrayList<String>>();
		HashMap<Observation, Integer> cubeSizes = new HashMap<Observation, Integer>();
		for(String gra : graphs){
			query = " DEFINE input:same-as \"yes\""
					+" SELECT DISTINCT ?observation ?dimension ?value" + 
					" FROM <"+gra+"> " +
					" FROM <codelists.age> " +
					" FROM <codelists.sex> " +
					" FROM <codelists.location> " +
					" FROM <codelists.admin.divisions> " +
					" FROM <codelists.sameas> " + 
					" WHERE {" + 
					"	?observation a qb:Observation ; ?dimension ?value . filter(?dimension!=rdf:type)" +
					//"   ?value imis:level ?level ." + 
					 "}";
			System.out.println(query);
			VirtuosoQueryExecution vqe = VirtuosoQueryExecutionFactory.create (query, graph);
			ResultSet results = vqe.execSelect();
			while(results.hasNext()){
				QuerySolution rs = results.next();
				Observation current;
				if(obs.get(rs.get("observation").toString())==null) {
					current = new Observation(rs.get("observation").toString());				
				}
				else{
					current = obs.get(rs.get("observation").toString());
				}
				
				Dimension dimension = DimensionFactory.getInstance().getDimensionByRepresentative(rs.get("dimension").toString());				
				if(dimension==null) {
					//System.out.println(rs.get("dimension").toString());
					/*current.setDimensionLevel(dimension, -1);
					current.setDimensionValue(dimension, "http://www.imis.athena-innovation.gr/def#TopConcept");
					obs.put(rs.get("observation").toString(), current);		*/			
					continue;
				}
				//System.out.println(dimension.toString());
				current.setDimensionValue(dimension, rs.get("value").toString());
				try{
					//System.out.println(levelMap.get(rs.get("value").toString()));
					current.setDimensionLevel(dimension, levelMap.get(rs.get("value").toString()));					
				}catch(NullPointerException e){
					//System.out.println(rs.get("value").toString());
					continue;
					//e.printStackTrace();
				}
				obs.put(rs.get("observation").toString(), current);					
				
				
			}
			vqe.close();
		}
		
		
		Set<String> obsKeys = obs.keySet();
		ArrayList<Dimension> allDims = DimensionFactory.getInstance().getDimensions();
		dims = null;
		
		for(String obsKey : obsKeys){
			Observation o = obs.get(obsKey);
			if(o.toString().equals("")) continue;
			//System.out.println(o.toString());
			dims = o.getDimensions();		
			for(Dimension curDim : allDims){
				if(!dims.contains(curDim)){
					//dims.add(curDim);
					o.setDimensionLevel(curDim, -1);
					o.setDimensionValue(curDim, "http://www.imis.athena-innovation.gr/def#TopConcept");
				}
			}
			
		}
		int previousCount = -1;
		for(String obsKey : obsKeys){
			Observation o = obs.get(obsKey);
			if(o.toString().equals("")) continue;			
				dims = o.getDimensions();
				if(previousCount==-1) previousCount = dims.size();
				else
				{
					if(previousCount!=dims.size()) System.out.println("Error in dims size. " + previousCount + " vs. " + dims.size());
					previousCount = dims.size();
				}						
		}
		ArrayList<String> cc;
		for(String obsKey : obsKeys){
			try{
				//System.out.println(obs.get(obsKey) + " ----- " + obsKey);						
				cubes.add(obs.get(obsKey));
				
				if(cubeBuckets.containsKey(obs.get(obsKey))) cc = cubeBuckets.get(obs.get(obsKey));
				else cc = new ArrayList<String>();
				cc.add(obsKey);
				cubeBuckets.put(obs.get(obsKey), cc);
			}catch(NullPointerException e){
				//System.out.println(obsKey);
				e.printStackTrace();
				break;
			}
			if(cubeSizes.containsKey(obs.get(obsKey))){
				cubeSizes.put(obs.get(obsKey), cubeSizes.get(obs.get(obsKey))+1);
			}
			else
				cubeSizes.put(obs.get(obsKey), 1);
					
		}
		
		System.out.println("Total observations: " + obs.size());
		System.out.println("Total unique cubes: " + cubes.size());
		System.out.println("Total cube Buckets: " + cubeBuckets.size());
		long elapsedTime = System.nanoTime() - start;
		System.out.println("Preprocessing - Elapsed time: " + elapsedTime);
		start = System.nanoTime();
		int obsCount = 0;
		for(Observation ob : cubeBuckets.keySet()){
			//System.out.println(cubeBuckets.get(ob).toString());
			obsCount += cubeBuckets.get(ob).size();
		}
		System.out.println("Total observations in buckets: " + obsCount);		
		int totes = 0;		
		
		int total = 0, c = 0;
		computed = 0;
		
			for(Observation obs1 : cubes){
								
				for(Observation obs2 : cubes){
					//if(obs1.equals(obs2)) continue;
					int cont = 0, cont_rev = 0;
					for(Dimension d : dims){
						
						if(obs1.getDimensionLevel(d)>=obs2.getDimensionLevel(d)) {				
							cont++;
						}
						if(obs2.getDimensionLevel(d)>=obs1.getDimensionLevel(d)) {
							cont_rev++;
						}
					}
					//full containment + complementarity
					if(cont==dims.size()) {
						total++;
						//totes += cubeSizes.get(obs1) * cubeSizes.get(obs2);															
						totes += cubeBuckets.get(obs1).size() * cubeBuckets.get(obs2).size();																	
						computeContainment(graph, obs1, obs2);													
					}
			}
			
		}
			
			
		System.out.println(total + " containment comparisons between cubes to be done.");
		System.out.println("Computed full: " + computed);		
		System.out.println("Total comparisons: " + totes);
		elapsedTime = System.nanoTime() - start;
		System.out.println("FULL CONTAINMENT: Elapsed time: " + elapsedTime);
		start = System.nanoTime();
		total = 0;
		c = 0;
		totes = 0;
		computedPartial = 0;
		for(Observation obs1 : cubes){
			
			for(Observation obs2 : cubes){
				//if(obs1.equals(obs2)) continue;
				int cont = 0, cont_rev = 0;
				for(Dimension d : dims){
					
					if(obs1.getDimensionLevel(d)>obs2.getDimensionLevel(d)) {				
						cont++;						
						break;
					}
					/*if(obs2.getDimensionLevel(d)>=obs1.getDimensionLevel(d)) {
						cont_rev++;
					}*/
				}				
				if(cont>0){
					computePartialContainment(graph, obs1, obs2);
				}				
		}
		
		}
		System.out.println(total + " containment comparisons between cubes to be done.");
		System.out.println("Computed full: " + computed);
		System.out.println("Computed partial: " + computedPartial);
		System.out.println("Total comparisons: " + totes);
		graph.close();
		elapsedTime = System.nanoTime() - start;
		System.out.println("PARTIAL CONTAINMENT: Elapsed time: " + elapsedTime);
		
		
	}
	
	public static boolean computeContainment(VirtGraph graph, Observation obs1, Observation obs2){
				
		//System.out.println(obs1.toString() + " with " + obs2.toString());
		int count;		
		ArrayList<String> o1 = cubeBuckets.get(obs1);
		ArrayList<String> o2 = cubeBuckets.get(obs2);
		TreeHierarchy.HierarchyNode node1, node2;
		for(String o1URI : o1){
			Observation o1Obs = obs.get(o1URI);
			for(String o2URI : o2){
				Observation o2Obs = obs.get(o2URI);
				count = 0;
				for(Dimension d : dims){
					if(o2Obs.getDimensionValue(d)==null || o2Obs.getDimensionValue(d).equals("top")){
						//count++;
						//continue;
						nulls++;
						break;
					}
					if(o1Obs.getDimensionLevel(d) < o2Obs.getDimensionLevel(d)) break; 
					node1 = hierarchy.getNode(o1Obs.getDimensionValue(d));
					node2 = hierarchy.getNode(o2Obs.getDimensionValue(d));
					if(node1==null) {
						System.out.println("Null1 Dimension " + d + "value" + o1Obs.getDimensionValue(d));
						nulls++;
						break;
					}
					if(node2==null) {
						System.out.println("Null2 Dimension " + d + "value" + o2Obs.getDimensionValue(d));
						nulls++;
						break;
					}
					if(node2.isParentOf(node1)){
						//System.out.println(node2.toString() + " is parent of " + node1.toString());
					//parents = inHierarchy(graph, o1Obs.getDimensionValue(d));
					//if(parents.contains(o2Obs.getDimensionValue(d))) {
						//System.out.println(node2.toString() + " is parent of " + node1.toString());
						count++;
					} else break;
				}
				if(count==dims.size()){
					/*System.out.println(obs2.toString()+"\n contains \n" + obs1.toString());
					System.out.println("Press Any Key To Continue...");
			        new java.util.Scanner(System.in).nextLine();*/
					computed++;					
				}
			}
		}
		
		//if(count>0)System.out.println(count);
		
		return false;
		
	}
	
	public static boolean computeContainmentNaive(VirtGraph graph, Observation obs1, Observation obs2){
		
		//System.out.println(obs1.toString() + " with " + obs2.toString());
		int count;				
		TreeHierarchy.HierarchyNode node1, node2;											
		count = 0;
		for(Dimension d : dims){
			if(obs2.getDimensionValue(d)==null || obs2.getDimensionValue(d).equals("top")){
				//count++;
				//continue;
				nulls++;
				break;
			}
			if(obs1.getDimensionLevel(d) < obs2.getDimensionLevel(d)) break; 
			node1 = hierarchy.getNode(obs1.getDimensionValue(d));
			node2 = hierarchy.getNode(obs2.getDimensionValue(d));
			if(node1==null) {
				System.out.println("Null1 Dimension " + d + "value" + obs1.getDimensionValue(d));
						nulls++;
						break;
					}
					if(node2==null) {
						System.out.println("Null2 Dimension " + d + "value" + obs2.getDimensionValue(d));
						nulls++;
						break;
					}
					if(node2.isParentOf(node1)){
						//System.out.println(node2.toString() + " is parent of " + node1.toString());
					//parents = inHierarchy(graph, o1Obs.getDimensionValue(d));
					//if(parents.contains(o2Obs.getDimensionValue(d))) {
						//System.out.println(node2.toString() + " is parent of " + node1.toString());
						count++;
					} else break;
				}
				if(count==dims.size()){
					/*System.out.println(obs2.toString()+"\n contains \n" + obs1.toString());
					System.out.println("Press Any Key To Continue...");
			        new java.util.Scanner(System.in).nextLine();*/
					computed++;					
				}
			
		
		
		//if(count>0)System.out.println(count);
		
		return false;
		
	}
	
	public static boolean computePartialContainment(VirtGraph graph, Observation obs1, Observation obs2){
		
		//System.out.println(obs1.toString() + " with " + obs2.toString());
		int count;		
		boolean isPartial = false;
		ArrayList<String> o1 = cubeBuckets.get(obs1);
		ArrayList<String> o2 = cubeBuckets.get(obs2);
		TreeHierarchy.HierarchyNode node1, node2;
		for(String o1URI : o1){
			Observation o1Obs = obs.get(o1URI);
			for(String o2URI : o2){
				Observation o2Obs = obs.get(o2URI);
				count = 0;
				for(Dimension d : dims){
					//if(o1Obs.getDimensionLevel(d) < o2Obs.getDimensionLevel(d)) break; 
					node1 = hierarchy.getNode(o1Obs.getDimensionValue(d));
					node2 = hierarchy.getNode(o2Obs.getDimensionValue(d));	
					if(o2Obs.getDimensionValue(d)==null || o2Obs.getDimensionValue(d).equals("top")){
						//count++;
						//continue;
						nulls++;
						break;
					}
					if(o1Obs.getDimensionLevel(d) < o2Obs.getDimensionLevel(d)) break; 
					node1 = hierarchy.getNode(o1Obs.getDimensionValue(d));
					node2 = hierarchy.getNode(o2Obs.getDimensionValue(d));
					if(node1==null) {
						System.out.println("Null1 Dimension " + d + "value" + o1Obs.getDimensionValue(d));
						nulls++;
						break;
					}
					if(node2==null) {
						System.out.println("Null2 Dimension " + d + "value" + o2Obs.getDimensionValue(d));
						nulls++;
						break;
					}
					if(node2.isParentOf(node1)){
						isPartial = true;						
						//break;
					}
				}				
			}
		}			
		if(isPartial) computedPartial++;
		return isPartial;
		
	}
	
public static boolean computePartialContainmentNaive(VirtGraph graph, Observation obs1, Observation obs2){
		
		//System.out.println(obs1.toString() + " with " + obs2.toString());
		int count;		
		boolean isPartial = false;		
		TreeHierarchy.HierarchyNode node1, node2;		
									
				count = 0;
				for(Dimension d : dims){
					//if(o1Obs.getDimensionLevel(d) < o2Obs.getDimensionLevel(d)) break; 
					node1 = hierarchy.getNode(obs1.getDimensionValue(d));
					node2 = hierarchy.getNode(obs2.getDimensionValue(d));	
					if(obs2.getDimensionValue(d)==null || obs2.getDimensionValue(d).equals("top")){
						//count++;
						//continue;
						nulls++;
						break;
					}
					if(obs1.getDimensionLevel(d) < obs2.getDimensionLevel(d)) break; 
					node1 = hierarchy.getNode(obs1.getDimensionValue(d));
					node2 = hierarchy.getNode(obs2.getDimensionValue(d));
					if(node1==null) {
						System.out.println("Null1 Dimension " + d + "value" + obs1.getDimensionValue(d));
						nulls++;
						break;
					}
					if(node2==null) {
						System.out.println("Null2 Dimension " + d + "value" + obs2.getDimensionValue(d));
						nulls++;
						break;
					}
					if(node2.isParentOf(node1)){
						isPartial = true;						
						//break;
					}
				}				
			
		if(isPartial) computedPartial++;
		return isPartial;
		
	}
	
	public static void getGraphStatistics(){
		VirtGraph graph = DataConnection.getConnection();
		int count = 0;
		HashSet<String> props = new HashSet<String>();
		for(String g : graphs){
			String query = "SELECT count(distinct ?obs) as ?count, count(distinct ?dim) as ?dimCount FROM <"+g+"> WHERE " +
					"{" +
						"?obs a qb:Observation ; ?dim ?o" +
					"}";
			VirtuosoQueryExecution vqe = VirtuosoQueryExecutionFactory.create (query, graph);
			ResultSet results = vqe.execSelect();			
			while(results.hasNext()){				
				QuerySolution rs = results.next();
				count += rs.getLiteral("count").getInt();
				System.out.println(g+" : " + rs.getLiteral("count").getInt() + ", " + rs.getLiteral("dimCount").getInt());								
			}
			vqe.close();			
			query = "SELECT distinct ?dim FROM <"+g+"> WHERE " +
					"{" +
						"?obs a qb:Observation ; ?dim ?o" +
					"}";
			vqe = VirtuosoQueryExecutionFactory.create (query, graph);
			results = vqe.execSelect();			
			while(results.hasNext()){				
				QuerySolution rs = results.next();								
				props.add(rs.get("dim").toString());
			}
			vqe.close();	
			
		}
		System.out.println("total : " + count);
		for(String prop : props ){
			System.out.println(prop);
		}
		graph.close();
	}
	
	public static void populateGraphs(){
		String[] graphsS = new String[] {
			"http://linked-statistics.gr/res_pop_by_citizenship_sex_age.php",
			"http://linked-statistics.gr/households_hmembers_by_hsize.php",
			"http://linked-statistics.gr/res_pop_by_age_sex_education.php",
			"http://estatwrap.ontologycentral.com/page/demo_r_births",
			"http://estatwrap.ontologycentral.com/page/demo_r_magec",
			"http://estatwrap.ontologycentral.com/page/nama_r_e3gdp",
			"http://estatwrap.ontologycentral.com/page/nama_r_e2rem"
		};
		for(String graph : graphsS){
			graphs.add(graph);
		}
		
	}
	
	public static void maps(ArrayList<Dimension> dimensions){
		VirtGraph graph = DataConnection.getConnection();
		HashSet<String> observations = new HashSet<String>();
		int count = 0;
		HashMap<String, ArrayList<Integer>> obsMap = new HashMap<String, ArrayList<Integer>>();
		for(Dimension d : dimensions){			
			String rep = d.getRepresentative();
			for(String named : graphs){
				String query = "SELECT DISTINCT ?obs ?norm FROM <"+named+"> FROM <codelists.sameas>" +
						"WHERE {" +
							"?obs <"+prefix+rep+"> ?o. " +
							"?norm owl:sameAs ?o" +
						"}";
				//System.out.println(query);
				VirtuosoQueryExecution vqe = VirtuosoQueryExecutionFactory.create (query, graph);
				ResultSet results = vqe.execSelect();			
				while(results.hasNext()){				
					QuerySolution rs = results.next();
					observations.add(rs.get("obs").toString());					
					count++;
				}
				vqe.close();	
			}
		}
		System.out.println(count);
		System.out.println(observations.size());
	}
	
	
	
	public static void observationMatrix(ArrayList<Dimension> dimensions){
		
		VirtGraph graph = DataConnection.getConnection();
		
		
		graph.close();
		
	}

	public static void createObservationMaps(ArrayList<Dimension> dimensions){
		
	
		VirtGraph graph = DataConnection.getConnection();
		int counter = 0;
		for(Dimension d : dimensions){
			
			System.out.println(d.getRepresentative());
			CSVWriter writer = null, labels = null;		
			try {
				  writer = new CSVWriter(new FileWriter("C:/Users/Marios/Desktop/multidimensional/"+d.getRepresentative()+"_data.csv"));
				  labels = new CSVWriter(new FileWriter("C:/Users/Marios/Desktop/multidimensional/"+d.getRepresentative()+"_labels.csv"));
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				
			}
			int obsCount = 0;
			int featCount = 0;
			ArrayList<String> observationList = new ArrayList<String>();
			//Dimension d = dimensions.get(0);
			for(String dataset : graphs){
				
				//for(Dimension d : dimensions){
					String dimQuery = "SELECT DISTINCT ?feature " +										
											"FROM <"+dataset+"> " +
											"FROM <codelists.sameas> " +
											"WHERE {" +
												"?obs <"+prefix+d.getRepresentative()+"> ?feat . " +
												"?feature owl:sameAs ?feat " +
											"}";
					System.out.println(dimQuery);
					VirtuosoQueryExecution vqe = VirtuosoQueryExecutionFactory.create (dimQuery, graph);
					ResultSet results = vqe.execSelect();			
					while(results.hasNext()){
						QuerySolution rs = results.next();
						String feature = rs.get("feature").toString();					
						if(!featureList.contains(feature)){
							featureList.add(feature);						
							featCount++;
						}
						
					}vqe.close();			
				//}		
				String[] featArr = new String[featureList.size()];
				for(int k=0; k<featureList.size(); k++ ) {
					String featLabel = featureList.get(k).substring(featureList.get(k).lastIndexOf("/")+1);
					if(featureList.get(k).lastIndexOf("#")>-1)
					featLabel = featureList.get(k).substring(featureList.get(k).lastIndexOf("#")+1);
					featArr[k] = featLabel;
				}
				labels.writeNext(featArr);
				String[] featNum = new String[featureList.size()];
				for(Integer num = 0; num<featNum.length; num++){
					featNum[num] = num.toString();
				}
				labels.writeNext(featNum);
				String meta = "SELECT DISTINCT ?observation " +				
						"FROM <"+dataset+"> " +
						"WHERE {" +
							"?observation ?p [] filter regex(iri(?p), \"imis.athena-innovation.gr\") }";
				VirtuosoQueryExecution vqeMeta = VirtuosoQueryExecutionFactory.create (meta, graph);
				ResultSet resultsMeta = vqeMeta.execSelect();	
				
				while(resultsMeta.hasNext()){
					QuerySolution rsMeta = resultsMeta.next();
					observationList.add(rsMeta.get("observation").toString());
					obsCount++;
				}vqeMeta.close();									
			}
			System.out.println("Feature list size: " + featureList.size());
			//System.out.println("Feature list: " + featureList.toString());
			System.out.println("Observation count: " + obsCount);
									
			int[][] matrix = new int[obsCount][featCount];
		/*	for(int i = 0 ; i <obsCount ; i++ ){
				for(int j = 0 ; j <featCount; j++ ){
					matrix[i][j] = 0;
				}
			}*/
			
			HashMap<String, String> obsLabels = new HashMap<String, String>();
			for(String dataset : graphs ){
				System.out.println("Graph " + dataset);
				String query = "SELECT DISTINCT ?observation ?o " +					
						"FROM <"+dataset+"> " +		
						"FROM <codelists.sameas> " + 
						"WHERE {" +
									"?observation <"+prefix+d.getRepresentative()+"> ?norm . ?o owl:sameAs ?norm " + 
									"}";			
				VirtuosoQueryExecution vqe = VirtuosoQueryExecutionFactory.create (query, graph);
				ResultSet results = vqe.execSelect();						
				while(results.hasNext()){				
					QuerySolution rs = results.next();
					String splitter = "/";
					//String object = ;
					if(rs.get("o").toString().contains("#")) splitter = "#";
					String obsLabel = rs.get("o").toString().substring(rs.get("o").toString().lastIndexOf(splitter)+1);
					
					String old = obsLabels.get(rs.get("observation").toString());
					if(old==null) {
						obsLabels.put(rs.get("observation").toString(), dataset+", " + obsLabel);
					}
					else obsLabels.put(rs.get("observation").toString(), old+", "+obsLabel);
						
									
					//String p = rs.get("p").toString();			
											
					String feature = rs.get("o").toString();
					//if(p.contains("http://purl.org/dc/terms/date")) feature = "http://reference.data.gov.uk/id/gregorian-year/"+feature;
					int index = featureList.indexOf(feature);
					//System.out.println(feature + " " + index);
					//System.out.println(rs.get("observation").toString());
					//System.out.println(obsCounter);
					matrix[observationList.indexOf(rs.get("observation").toString())][index] = 1;
					//observationRow[index] = "1";
					ArrayList<String> parents = inHierarchy(graph, feature);
					for(String parent : parents){
						//System.out.println(parents.toString());
						int parentIndex = featureList.indexOf(parent);
						if(parentIndex>-1){
							//System.out.println("Got parent " + parent + " of " + feature);
							//matrix[obsCount][parentIndex] = 1;
							//observationRow[parentIndex] = "1";
							matrix[observationList.indexOf(rs.get("observation").toString())][parentIndex] = 1;
						}
					}			
					
					
					//System.out.println(obsCount);
				}vqe.close();
			}
			
			for(int i=0 ; i<obsCount ; i++){
				String[] row = new String[featCount];
				labels.writeNext(new String[] {"["+obsLabels.get(observationList.get(i))+"]"});
				for(int j =0; j<featCount ; j++){
					row[j] = new Integer(matrix[i][j]).toString();
				}
				writer.writeNext(row);
				
			}
			try {
				writer.close();
			} catch (IOException e) {			
				e.printStackTrace();
			}
			try {
				labels.close();
			} catch (IOException e) {			
				e.printStackTrace();
			}
			featureList.clear();
		}
		
		graph.close();
	}

	
	
	
	public static void createHierarchyMap(){
		
		VirtGraph graph = DataConnection.getConnection();		
		String query = " SELECT DISTINCT ?value ?parent" + 					
				" FROM <codelists.age> " +
				" FROM <codelists.sex> " +
				" FROM <codelists.location> " +
				" FROM <codelists.admin.divisions> " +
				" FROM <codelists.sameas> " +
				" WHERE {" + 					
				"   {?value skos:broaderTransitive ?parent }" + 
				"UNION " +
				"   {?dummy owl:sameAs ?value . ?value skos:broaderTransitive ?parent }" + 
				"UNION " +
				"   {?value owl:sameAs ?dummy . ?dummy skos:broaderTransitive ?parent }" +
				 "}";
		//System.out.println(query);
		VirtuosoQueryExecution vqe = VirtuosoQueryExecutionFactory.create (query, graph);
		ResultSet results = vqe.execSelect();			
		while(results.hasNext()){				
			QuerySolution rs = results.next();
			//String value = ;
			if(rs.get("value").toString()==null || rs.get("parent").toString()==null) continue;
			//if(rs.get("parent").toString().contains("null")) System.out.println(rs.get("parent").toString());
			/*TreeHierarchy.HierarchyNode node = hierarchy.new HierarchyNode(rs.get("value").toString());
			TreeHierarchy.HierarchyNode parent = hierarchy.new HierarchyNode(rs.get("parent").toString());*/
			TreeHierarchy.HierarchyNode node = hierarchy.insertIntoSet(rs.get("value").toString());
			TreeHierarchy.HierarchyNode parent = hierarchy.insertIntoSet(rs.get("parent").toString());
			node.setParent(parent);
			//System.out.println(node.toString() + " has parent " + node.parent.toString());
		}
		vqe.close();
		query = " SELECT DISTINCT ?value ?parent" + 					
				" FROM <codelists.age> " +
				" FROM <codelists.sex> " +
				" FROM <codelists.location> " +
				" FROM <codelists.admin.divisions> " +
				" FROM <codelists.sameas> " +
				" WHERE {" + 					
				"   {?value skos:broaderTransitive ?parent }" + 
				"UNION " +
				"   {?dummy owl:sameAs ?value . ?value skos:broaderTransitive ?parent }" + 
				"UNION " +
				"   {?value owl:sameAs ?dummy . ?dummy skos:broaderTransitive ?parent }" +
				 "}";		
		vqe = VirtuosoQueryExecutionFactory.create (query, graph);
		results = vqe.execSelect();			
		while(results.hasNext()){				
			QuerySolution rs = results.next();
			//String value = ;
			TreeHierarchy.HierarchyNode node = hierarchy.insertIntoSet(rs.get("value").toString());
			node.setParent(hierarchy.insertIntoSet(rs.get("parent").toString()));
		}
		vqe.close();
		/*Set<String> keySet = hierarchy.keySet();
		for(String key : keySet){
			if(hierarchy.getNode(key).parent == null) hierarchy.getNode(key).parent
		}*/
		System.out.println(hierarchy.size() + " values in hierarchy.");
		
	}
	
	
	public static ArrayList<String> inHierarchy(VirtGraph graph, String feature){
	
	
		ArrayList<String> parents = new ArrayList<String>();
		//if(feature.contains("/EL")) feature = feature.replace("/EL", "/GR");
		//if(feature.contains("/dic/geo#")) feature = feature.replace("/dic/geo#", "http://ec.europa.eu/eurostat/ramon/rdfdata/nuts2008/");
		String query = "SELECT ?parent " +
					"FROM <codelists.sex> " +
					"FROM <codelists.location> " +
					"FROM <codelists.age> " +
					"FROM <codelists.admin.divisions> " + 
					"FROM <codelists.sameas> " + 
					//"FROM <codelists.time> " +
					"WHERE {" +
							"{"
							+ "{<"+feature+"> skos:broaderTransitive/skos:broaderTransitive* ?parent }" +
							//"UNION {<"+feature+"> skos:broaderTransitive/skos:broaderTransitive* [owl:sameAs ?parent] }" +
							"}" +
							"UNION " + 
							"{" +
							 "{?dummy owl:sameAs <"+feature+"> ; skos:broaderTransitive/skos:broaderTransitive* ?parent }" +
							// "UNION {?dummy owl:sameAs <"+feature+"> ; skos:broaderTransitive/skos:broaderTransitive* [owl:sameAs ?parent] }" +
						    "}" +
							"}";
		//System.out.println(query);
		VirtuosoQueryExecution vqe = VirtuosoQueryExecutionFactory.create (query, graph);
		ResultSet results = vqe.execSelect();		
		while(results.hasNext()){
			QuerySolution rs = results.next();
			parents.add(rs.get("parent").toString().replace("/GR", "/EL"));
			//System.out.println(feature+" has parent " + rs.get("parent").toString());
		}vqe.close();
		/*if(feature.contains("gregorian")){
			//System.out.println(feature);
			String date = feature.substring(feature.lastIndexOf("/")+1);
			if(date.contains("M")) parents.add("http://reference.data.gov.uk/id/gregorian-year/"+date.substring(0,3));
		}		
		if(feature.contains("sex-M") || feature.contains("sex-F")){
			parents.add(feature.replaceAll("sex-M", "sex-T").replaceAll("sex-F", "sex-T"));			
			
		}*/
		return parents;
	}
	
	
	public static void createHashMapNaive(){
		VirtGraph graph = DataConnection.getConnection();
		String query;
		obs = new HashMap<String, Observation>();						
		for(String gra : graphs){
			query = " DEFINE input:same-as \"yes\""
					+" SELECT DISTINCT ?observation ?dimension ?value" + 
					" FROM <"+gra+"> " +
					" FROM <codelists.age> " +
					" FROM <codelists.sex> " +
					" FROM <codelists.location> " +
					" FROM <codelists.admin.divisions> " +
					" FROM <codelists.sameas> " + 
					" WHERE {" + 
					"	?observation a qb:Observation ; ?dimension ?value . filter(?dimension!=rdf:type)" +
					//"   ?value imis:level ?level ." + 
					 "}";			
			VirtuosoQueryExecution vqe = VirtuosoQueryExecutionFactory.create (query, graph);
			ResultSet results = vqe.execSelect();
			while(results.hasNext()){
				QuerySolution rs = results.next();
				Observation current;
				if(obs.get(rs.get("observation").toString())==null) {
					current = new Observation(rs.get("observation").toString());				
				}
				else{
					current = obs.get(rs.get("observation").toString());
				}
				
				Dimension dimension = DimensionFactory.getInstance().getDimensionByRepresentative(rs.get("dimension").toString());				
				if(dimension==null) {
					//System.out.println(rs.get("dimension").toString());
					/*current.setDimensionLevel(dimension, -1);
					current.setDimensionValue(dimension, "http://www.imis.athena-innovation.gr/def#TopConcept");
					obs.put(rs.get("observation").toString(), current);		*/			
					continue;
				}
				//System.out.println(dimension.toString());
				current.setDimensionValue(dimension, rs.get("value").toString());
				try{
					//System.out.println(levelMap.get(rs.get("value").toString()));
					current.setDimensionLevel(dimension, levelMap.get(rs.get("value").toString()));					
				}catch(NullPointerException e){
					//System.out.println(rs.get("value").toString());
					continue;
					//e.printStackTrace();
				}
				obs.put(rs.get("observation").toString(), current);					
				
				
			}
			vqe.close();
		}
		
		
		Set<String> obsKeys = obs.keySet();
		ArrayList<Dimension> allDims = DimensionFactory.getInstance().getDimensions();
		dims = null;
		
		for(String obsKey : obsKeys){
			Observation o = obs.get(obsKey);
			if(o.toString().equals("")) continue;
			//System.out.println(o.toString());
			dims = o.getDimensions();		
			for(Dimension curDim : allDims){
				if(!dims.contains(curDim)){
					//dims.add(curDim);
					o.setDimensionLevel(curDim, -1);
					o.setDimensionValue(curDim, "http://www.imis.athena-innovation.gr/def#TopConcept");
				}
			}
			
		}
		int previousCount = -1;
		for(String obsKey : obsKeys){
			Observation o = obs.get(obsKey);
			if(o.toString().equals("")) continue;			
				dims = o.getDimensions();
				if(previousCount==-1) previousCount = dims.size();
				else
				{
					if(previousCount!=dims.size()) System.out.println("Error in dims size. " + previousCount + " vs. " + dims.size());
					previousCount = dims.size();
				}						
		}
		ArrayList<String> cc;		
		
		System.out.println("Total observations: " + obs.size());				
		long elapsedTime = System.nanoTime() - start;
		System.out.println("Preprocessing - Elapsed time: " + elapsedTime);
		start = System.nanoTime();
		int obsCount = 0;		
		long totes = 0;		
		
		int total = 0, c = 0;
		computed = 0;
		
			//for(Observation obs1 : obs){
		for(String obsString1 : obs.keySet()){
			Observation obs1 = obs.get(obsString1);
								
			for(String obsString2 : obs.keySet()){
				Observation obs2 = obs.get(obsString2);
					totes++;
					int cont = 0, cont_rev = 0;
					for(Dimension d : dims){
						
						if(obs1.getDimensionLevel(d)>=obs2.getDimensionLevel(d)) {				
							cont++;
						}
						if(obs2.getDimensionLevel(d)>=obs1.getDimensionLevel(d)) {
							cont_rev++;
						}
					}
					//full containment + complementarity
					if(cont==dims.size()) {
						//total++;
						//totes += cubeSizes.get(obs1) * cubeSizes.get(obs2);															
						//totes += cubeBuckets.get(obs1).size() * cubeBuckets.get(obs2).size();																	
						computeContainmentNaive(graph, obs1, obs2);													
					}
			}
			
		}
			
			
		System.out.println(total + " containment comparisons between cubes to be done.");
		System.out.println("Computed full: " + computed);		
		System.out.println("Total comparisons: " + totes);
		elapsedTime = System.nanoTime() - start;
		System.out.println("FULL CONTAINMENT: Elapsed time: " + elapsedTime);
		start = System.nanoTime();
		total = 0;
		c = 0;
		totes = 0;
		computedPartial = 0;
		for(String obsString1 : obs.keySet()){
			Observation obs1 = obs.get(obsString1);
								
			for(String obsString2 : obs.keySet()){
				Observation obs2 = obs.get(obsString2);
				int cont = 0, cont_rev = 0;
				for(Dimension d : dims){
					
					if(obs1.getDimensionLevel(d)>obs2.getDimensionLevel(d)) {				
						cont++;						
						break;
					}
					/*if(obs2.getDimensionLevel(d)>=obs1.getDimensionLevel(d)) {
						cont_rev++;
					}*/
				}				
				if(cont>0){
					computePartialContainmentNaive(graph, obs1, obs2);
				}				
		}
		
		}
		System.out.println(total + " containment comparisons between cubes to be done.");
		System.out.println("Computed full: " + computed);
		System.out.println("Computed partial: " + computedPartial);
		System.out.println("Total comparisons: " + totes);
		graph.close();
		elapsedTime = System.nanoTime() - start;
		System.out.println("PARTIAL CONTAINMENT: Elapsed time: " + elapsedTime);
		
		
	}
	
	
	public static void createDataset(){
		VirtGraph graph = DataConnection.getConnection();		
		obs = new HashMap<String, Observation>();
		String query;
		
		for(String gra : graphs){
			query = " DEFINE input:same-as \"yes\""
					+" SELECT DISTINCT ?observation ?dimension ?value" + 
					" FROM <"+gra+"> " +
					" FROM <codelists.age> " +
					" FROM <codelists.sex> " +
					" FROM <codelists.location> " +
					" FROM <codelists.admin.divisions> " +
					" FROM <codelists.sameas> " + 
					" WHERE {" + 
					"	?observation a qb:Observation ; ?dimension ?value . filter(?dimension!=rdf:type)" +
					//"   ?value imis:level ?level ." + 
					 "}";
			System.out.println(query);
			VirtuosoQueryExecution vqe = VirtuosoQueryExecutionFactory.create (query, graph);
			ResultSet results = vqe.execSelect();
			while(results.hasNext()){
				QuerySolution rs = results.next();
				Observation current;
				if(obs.get(rs.get("observation").toString())==null) {
					current = new Observation(rs.get("observation").toString());				
				}
				else{
					current = obs.get(rs.get("observation").toString());
				}
				
				Dimension dimension = DimensionFactory.getInstance().getDimensionByRepresentative(rs.get("dimension").toString());				
				if(dimension==null) {					
					continue;
				}				
				current.setDimensionValue(dimension, rs.get("value").toString());
				try{					
					current.setDimensionLevel(dimension, levelMap.get(rs.get("value").toString()));					
				}catch(NullPointerException e){					
					continue;					
				}
				obs.put(rs.get("observation").toString(), current);													
			}
			vqe.close();
		}
		int success = 0, fail = 0;		
		Dataset dataset = new DefaultDataset();
		
		ArrayList<Dimension> allDims = DimensionFactory.getInstance().getDimensions();
		for(String obsKey : obs.keySet()){
			Observation o = obs.get(obsKey);
			if(o.toString().equals("")) continue;
			//System.out.println(o.toString());
			dims = o.getDimensions();		
			Instance obsInstance = new SparseInstance();			
			for(Dimension curDim : allDims){
				if(!dims.contains(curDim)){
					o.setDimensionLevel(curDim, -1);
					o.setDimensionValue(curDim, "http://www.imis.athena-innovation.gr/def#TopConcept");
				}				
				int index = valueIndexMap.get(o.getDimensionValue(curDim));
				obsInstance.put(index, 1.0);				
				try{
					//System.out.println(hierarchy.getNode(o.getDimensionValue(curDim)).toString());
					ArrayList<String> parents = hierarchy.getNode(o.getDimensionValue(curDim)).getParents();
					if(parents==null) {
						fail++;
						//System.out.println("FAIL: " + o.getDimensionValue(curDim));
						continue;
					}
					//System.out.println("SUCCESS " + o.getDimensionValue(curDim) + " parents: " + parents.toString());
					for(String parent : parents){
						index = valueIndexMap.get(parent);
						obsInstance.put(index, 1.0);
					}
					success++;
				}catch(Exception e){
					//e.printStackTrace();
				}
			}
			dataset.add(obsInstance);			
		}
		System.out.println("Dataset size: " + dataset.size());
		System.out.println("Success: " + success);
		System.out.println("Fail: " + fail);
			
		Iterator<Instance> it = dataset.iterator();
		ToWekaUtils t = new ToWekaUtils(dataset);		
		Instances wekaD = t.getDataset(); 
		/*while(it.hasNext()){
			SparseInstance next = (SparseInstance) it.next();
			weka.core.Instance sp = t.instanceToWeka(next);				
			//weka.core.SparseInstance sp = new weka.core.SparseInstance(1.0, vector);
			Enumeration en = sp.enumerateAttributes();
			Instances wekaD = new Instances("dataset", null, dataset.size());
			System.out.println("000000000000000000000000000000000000000000000");
			int count = 0;
			while(en.hasMoreElements()){
				Attribute at = (Attribute) en.nextElement();
				System.out.println(count + " " + at.name());
			}
			System.out.println("000000000000000000000000000000000000000000000");
			//wekaD.add(sp);
		}*/
		System.out.println("Weka dataset: " + wekaD.numInstances());
		
		//if(true) return;
		long start = System.nanoTime();
		
		/*Clusterer km = new KMeans(numberOfClusters);
		System.out.println("Sampling...");
		Sampling s=Sampling.SubSampling;
		Pair<Dataset, Dataset> datas=s.sample(dataset, (int)(dataset.size()*0.1));
		System.out.println("Done sampling.");*/
		String[] options = new String[2];
		 options[0] = "-N";                 // max. iterations
		 options[1] = "10";
		 try{
			 Canopy canopy = new Canopy();   // new instance of clusterer
			 canopy.setOptions(options);     // set the options
			 canopy.buildClusterer(wekaD);    // build the clusterer
			 System.out.println(canopy.getNumClusters());
			 System.out.println(canopy.getMinimumCanopyDensity());
			 //System.out.println(canopy.);
			 /*ClusterEvaluation eval = new ClusterEvaluation();
			 eval.setClusterer(canopy);                                   // the cluster to evaluate
			 eval.evaluateClusterer();                                // data to evaluate the clusterer on
			 System.out.println("# of clusters: " + eval.getNumClusters());  // output # of clusters
*/		 }catch(Exception e){
			 e.printStackTrace();
		 }
		 
		
		/* Cluster the data, it will be returned as an array of data sets, with
		  * each dataset representing a cluster. */
		/*System.out.println("Starting Clusterer with " + numberOfClusters + " clusters...");
		Dataset[] clusters = km.cluster(datas.x());
		System.out.println(clusters.length);
		long elapsedTime = System.nanoTime() - start;
		System.out.println("Clustering end. Time: " + elapsedTime);
		ClusterEvaluation sse= new SumOfSquaredErrors();
		 Measure the quality of the clustering 
		double score=sse.score(clusters);
		System.out.println("Evaluation score: " + score);*/
		/*for(String obsURI : obs.keySet()){			
			Instance obsInst = new SparseInstance();
			for()
		}*/
	}
	
}
