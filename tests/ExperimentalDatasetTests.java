package adasdasd;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import models.Dimension;
import models.DimensionFactory;
import models.Observation;
import models.TreeHierarchy;
import virtuoso.jena.driver.VirtGraph;
import virtuoso.jena.driver.VirtuosoQueryExecution;
import virtuoso.jena.driver.VirtuosoQueryExecutionFactory;
import weka.attributeSelection.LatentSemanticAnalysis;
import weka.attributeSelection.Ranker;
import weka.clusterers.CLOPE;
import weka.clusterers.ClusterEvaluation;
import weka.clusterers.DBSCAN;
import weka.clusterers.FarthestFirst;
import weka.clusterers.HierarchicalClusterer;
import weka.clusterers.RandomizableClusterer;
import weka.clusterers.XMeans;
import weka.core.Attribute;
import weka.core.ChebyshevDistance;
import weka.core.EuclideanDistance;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.ManhattanDistance;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;
import app.DataConnection;
import au.com.bytecode.opencsv.CSVWriter;

import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;


public class ExperimentalDatasetTests {

	/**
	 * @param args
	 */
	static HashSet<String> graphs = new HashSet<String>();
	static String connectionString = "jdbc:virtuoso://localhost:1111/autoReconnect=true/charset=UTF-8/log_enable=2";
	static String prefix = "http://imis.athena-innovation.gr/def#";
	static String codePrefix ="http://imis.athena-innovation.gr/code#";
	static ArrayList<String> featureList;
	static HashMap<String, Integer> levelMap;
	static HashMap<String, Integer> valueIndexMap;
	static HashMap<Integer, String> indexValueMap;
	//static HashMap<String, Integer> levelMap = new HashMap<String, Integer>();
	static TreeHierarchy hierarchy;
	static HashMap<String, Observation> obs;
	static Set<Dimension> dims; 
	static HashMap<Observation, ArrayList<String>> cubeBuckets;
	static long computed, computedPartial, nulls, start;
	static int nextIndex = 0;
	static int numberOfClusters = 10;
	static boolean featSelect = false;
	static VirtGraph graph;
	static HashSet<String> partialComparisonSet;
	static long[] partialContainments = new long[3];
	static long[] fullContainments = new long[3];
	static boolean produceNew = false;
	
	public static void main(String[] args) {
				
		init();
		runAllTests(args);		
		System.out.println("-----------------------------------------------------");
		System.out.println("-----------------------------------------------------");
		System.out.println(Arrays.toString(partialContainments));
		System.out.println(Arrays.toString(fullContainments));
		double[] clusteringRecall = new double[]{
				(double) partialContainments[1]/partialContainments[0], 
				(double) fullContainments[1]/fullContainments[0] 
		} ;
		System.out.println("Recalls: " + Arrays.toString(clusteringRecall));	
		
	}
	

	public static long runAllTests(String[] args){
		
		graph = DataConnection.getConnection();
		hierarchy = new TreeHierarchy(new File(args[0]));
		if(args.length>1) numberOfClusters = Integer.parseInt(args[1]);
		start = System.nanoTime();    					
		populateGraphs();
		createLevelMap();
		createHierarchyMap();
		createDatasetMaps(0);
		System.out.println("Done pre-processing batch");
		System.out.println("Testing masks...");
		createHashMap();
		System.out.println("-----------------------------------------------------");
		System.out.println("Testing naive...");
		createHashMapNaive();
		System.out.println("-----------------------------------------------------");
		System.out.println("Testing clustering...");
		createHashMapClustering();
		System.out.println("-----------------------------------------------------");
		long elapsedTime = System.nanoTime() - start;	//on a single node, otherwise run CPUTime.now()
		return elapsedTime;
	}
	
	public static void init(){
		computed = 0;
		computedPartial = 0;
		nulls = 0;
		featureList = new ArrayList<String>();
		levelMap = new HashMap<String, Integer>();
		valueIndexMap = new HashMap<String, Integer>(); 
		indexValueMap = new HashMap<Integer, String>();
		partialComparisonSet = new HashSet<String>();
	}
	
		
	public static void createLevelMap(){		
		
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
			int nextInt = getNextInt();
			valueIndexMap.put(rs.get("value").toString(), nextInt);
			//indexValueMap.put(nextInt, rs.get("value").toString());
		}
		vqe.close();				
	
		//System.out.println(levelMap.size());
	}
	
	public static int getNextInt(){
		return nextIndex++;
	}
	
	public static void createDatasetMaps(int limit){
		
		obs = new HashMap<String, Observation>();
		if(limit==0) limit = Integer.MAX_VALUE;
		for(String gra : graphs){
			//if(!gra.contains("births")) continue;
			String query = " DEFINE input:same-as \"yes\""
					+" SELECT DISTINCT ?observation ?dimension ?value" + 
					" FROM <"+gra+"> " +
					" FROM <codelists.age> " +
					" FROM <codelists.sex> " +
					" FROM <codelists.location> " +
					" FROM <codelists.admin.divisions> " +
					" FROM <codelists.sameas> " + 
					" WHERE {" + 
					"	?observation a qb:Observation . "
					+ "{?observation ?dimension ?value . } UNION {?observation ?dimension ?v . ?v owl:sameAs ?value} "
					+ "filter(?dimension!=rdf:type)" +					
					//"   ?value imis:level ?level ." + 
					 //"} ORDER BY ASC(?observation) LIMIT 10000";
					"}  ";
			System.out.println("query: " + query);
			VirtuosoQueryExecution vqe = VirtuosoQueryExecutionFactory.create (query, graph);
			ResultSet results = vqe.execSelect();
			int resultCount = 0, notNull = 0, exc = 0, nulls = 0;
			
			while(results.hasNext()  && resultCount <= limit ){
				resultCount++;
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
					nulls++;
					continue;
				}
				//System.out.println(dimension.toString());
				current.setDimensionValue(dimension, rs.get("value").toString());
				try{
					//System.out.println(levelMap.get(rs.get("value").toString()));
					current.setDimensionLevel(dimension, levelMap.get(rs.get("value").toString()));					
				}catch(NullPointerException e){
					//System.out.println(rs.get("value").toString());
					exc++;					
					continue;
					//e.printStackTrace();
				}
				obs.put(rs.get("observation").toString(), current);					
				notNull++;
				
				
			}
			vqe.close();
			/*System.out.println(gra + " not null " + notNull);
			System.out.println(gra + " nulls " + nulls);
			System.out.println(gra + " exceptions " + exc);*/
		}
		if(!produceNew) return;
		int moreC = 0;
		HashMap<String, Observation> new_obs = new HashMap<String, Observation>();
		for(String obs1 : obs.keySet()){
			Observation ob = obs.get(obs1);
			dims = ob.getDimensions();		
			for(Dimension curDim : DimensionFactory.getInstance().getDimensions()){
				if(!dims.contains(curDim)){
					//dims.add(curDim);
					ob.setDimensionLevel(curDim, -1);
					ob.setDimensionValue(curDim, "http://www.imis.athena-innovation.gr/def#TopConcept");
				}
			}
			for(int larger = 0; larger < 5 ; larger++){
				Observation o = new Observation(obs.get(obs1).toString()+"_"+moreC);
				for(Dimension d : DimensionFactory.getInstance().getDimensions()){
					int newDimLevel = obs.get(obs1).getDimensionLevel(d);
					//if(newDimLevel>0) newDimLevel++;
					o.setDimensionLevel(d, newDimLevel);
					o.setDimensionValue(d, obs.get(obs1).getDimensionValue(d));//+"_"+moreC);
				}
				moreC++;
				//moreCubes.add(o);
				new_obs.put(obs.get(obs1).toString()+"_"+moreC, o);
			}
			new_obs.put(obs1, obs.get(obs1));
		}
		obs = new_obs;
		
	}
	
	public static void createHashMap(){
		
		String query;
		
		//Xrhsimopoiw to Observation class gia Observations kai Cubes tautoxrona...
		HashSet<Observation> cubes = new HashSet<Observation>();
		cubeBuckets = new HashMap<Observation, ArrayList<String>>();
		HashMap<Observation, Integer> cubeSizes = new HashMap<Observation, Integer>();
		
		
		
		
		ArrayList<Dimension> allDims = DimensionFactory.getInstance().getDimensions();
		dims = null;
		
		//HashSet<Observation> moreCubes = new HashSet<Observation>();
		//moreCubes.addAll(cubes);
		Set<String> obsKeys = obs.keySet();
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
		
		obsKeys = obs.keySet();
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
		
		int total = 0, c = 0, f_o = 0;
		computed = 0;
		
		//for(int i = 0 ; i < 10 ; i++){
		HashSet<String> fullContainmentMap = new HashSet<String>();
			for(Observation obs1 : cubes){

				//for(int j = 0; j < Math.sqrt(i); j++){
					
					for(Observation obs2 : cubes){
						
						int cont = 0;
						f_o++;
						for(Dimension d : dims){
													
							if(obs1.getDimensionLevel(d)>=obs2.getDimensionLevel(d)) {				
								cont++;
							}							
						}
						//full containment + complementarity
						if(cont==dims.size()) {
							//total++;																
							computeContainment(graph, obs1, obs2, 0, fullContainmentMap);
						}
				}
				//}
				
			}
			
			/*for(Observation obs1 : cubes){

				//for(int j = 0; j < Math.sqrt(i); j++){
					
					for(Observation obs2 : cubes){
						
						int cont = 0;
						f_o++;
						for(Dimension d : dims){
													
							if(obs1.getDimensionLevel(d)>=obs2.getDimensionLevel(d)) {				
								cont++;
							}							
						}
						//full containment + complementarity
						if(cont==dims.size()) {
							//total++;																
							computeContainment(graph, obs1, obs2, 0);
						}
				}
			}*/
			
			
		//}
			
		System.out.println("F_O: " + f_o);			
		System.out.println("Computed full: " + computed);		
		//System.out.println("Total comparisons: " + totes);
		elapsedTime = System.nanoTime() - start;
		System.out.println("FULL CONTAINMENT: Elapsed time: " + elapsedTime);
		long complement = 0;
		for(String pair : fullContainmentMap){
			String[] obsPair = pair.split("_zzz_");
			String newPair = obsPair[1]+"_zzz_"+obsPair[0];
			if(fullContainmentMap.contains(newPair))
				complement++;
		}
		
		//if(true) return;
		//start = System.nanoTime();
		System.out.println("Computed complement: " + complement);		
		//System.out.println("Total comparisons: " + totes);
		elapsedTime = System.nanoTime() - start;
		System.out.println("COMPLEMENTARITY: Elapsed time: " + elapsedTime);
		start = System.nanoTime();
		total = 0;
		c = 0;
		totes = 0;
		computedPartial = 0;
		int partialComparisons = 0;
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
					partialComparisons++;
					computePartialContainment(graph, obs1, obs2, 0);
				}				
		}
		
		}
		System.out.println(total + " containment comparisons between cubes to be done.");
		System.out.println("Computed full: " + computed);
		System.out.println("Computed partial: " + computedPartial);		
		elapsedTime = System.nanoTime() - start;
		System.out.println("PARTIAL CONTAINMENT: Elapsed time: " + elapsedTime);
		/*partialContainments[0] = computedPartial;
		fullContainments[0] = computed;*/
		
	}
	
	public static boolean computeContainment(VirtGraph graph, Observation obs1, Observation obs2, int methodIndex){
				
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
						//System.out.println("Null1 Dimension " + d + "value" + o1Obs.getDimensionValue(d));
						//nulls++;
						break;
					}
					if(node2==null) {
						//System.out.println("Null2 Dimension " + d + "value" + o2Obs.getDimensionValue(d));
						//nulls++;
						break;
					}
					//try{
						if(node2.isParentOf(node1)){
							//System.out.println(node2.toString() + " is parent of " + node1.toString());
						//parents = inHierarchy(graph, o1Obs.getDimensionValue(d));
						//if(parents.contains(o2Obs.getDimensionValue(d))) {
							//System.out.println(node2.toString() + " is parent of " + node1.toString());
							count++;
						} else break;
					/*}
					catch(Exception e){
						break;
					}*/
					
				}
				if(count==dims.size()){
					/*System.out.println(obs2.toString()+"\n contains \n" + obs1.toString());
					System.out.println("Press Any Key To Continue...");
			        new java.util.Scanner(System.in).nextLine();*/
					fullContainments[methodIndex]++;
					computed++;					
				}
			}
		}
		
		//if(count>0)System.out.println(count);
		
		return false;
		
	}
	
	public static HashSet<String> computeContainment(VirtGraph graph, Observation obs1, Observation obs2, int methodIndex, HashSet<String> fullContainmentMap){
		
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
						//System.out.println("Null1 Dimension " + d + "value" + o1Obs.getDimensionValue(d));
						//nulls++;
						break;
					}
					if(node2==null) {
						//System.out.println("Null2 Dimension " + d + "value" + o2Obs.getDimensionValue(d));
						//nulls++;
						break;
					}
					//try{
						if(node2.isParentOf(node1)){
							//System.out.println(node2.toString() + " is parent of " + node1.toString());
						//parents = inHierarchy(graph, o1Obs.getDimensionValue(d));
						//if(parents.contains(o2Obs.getDimensionValue(d))) {
							//System.out.println(node2.toString() + " is parent of " + node1.toString());
							count++;
						} else break;
					/*}
					catch(Exception e){
						break;
					}*/
					
				}
				if(count==dims.size()){
					/*System.out.println(obs2.toString()+"\n contains \n" + obs1.toString());
					System.out.println("Press Any Key To Continue...");
			        new java.util.Scanner(System.in).nextLine();*/
					fullContainments[methodIndex]++;
					fullContainmentMap.add(obs1.toString()+"_zzz_"+obs2.toString());
					computed++;					
				}
			}
		}
		
		//if(count>0)System.out.println(count);
		
		return fullContainmentMap;
		
	}
	
	
	
	public static boolean computeContainmentNaive(VirtGraph graph, Observation obs1, Observation obs2, int methodIndex){
				
		int count = 0;
		TreeHierarchy.HierarchyNode node1 = null, node2 = null;													
		for(Dimension d : dims){
			if(obs2.getDimensionValue(d)==null || obs2.getDimensionValue(d).equals("top")){				
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
				count++;
			} else break;
		}
		if(count==dims.size()){		
			computed++;	
			//System.out.println(obs1.toString() + " " + obs2.toString());
			fullContainments[methodIndex]++;
		}
					
		return false;		
	}
	
	public static boolean computePartialContainment(VirtGraph graph, Observation obs1, Observation obs2, int methodIndex){
		
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
					if(o2Obs.getDimensionValue(d)==null || o2Obs.getDimensionValue(d).equals("top")
							|| o1Obs.getDimensionValue(d)==null || o1Obs.getDimensionValue(d).equals("top")){
/*					if(o2Obs.getDimensionValue(d)==null 
							|| o1Obs.getDimensionValue(d)==null || o1Obs.getDimensionLevel(d)<=0){*/
						//count++;
						//continue;
						nulls++;
						break;
					}
					if(o1Obs.getDimensionLevel(d) < o2Obs.getDimensionLevel(d)) break; 
					node1 = hierarchy.getNode(o1Obs.getDimensionValue(d));
					node2 = hierarchy.getNode(o2Obs.getDimensionValue(d));
					/*if(node1==null) {
						System.out.println("Null1 Dimension " + d + "value" + o1Obs.getDimensionValue(d));
						nulls++;
						break;
					}
					if(node2==null) {
						System.out.println("Null2 Dimension " + d + "value" + o2Obs.getDimensionValue(d));
						nulls++;
						break;
					}*/
					if(node2.isParentOf(node1)){
						isPartial = true;						
						//break;
					}
				}	
				if(isPartial) {
					computedPartial++;
					isPartial = false;
					partialContainments[methodIndex]++;
					//partialComparisonSet.add(//e)
				}
			}
			
		}			
		
		return isPartial;
		
	}
	
public static boolean computePartialContainmentNaive(VirtGraph graph, Observation obs1, Observation obs2, int methodIndex){
		
		//System.out.println(obs1.toString() + " with " + obs2.toString());		
		boolean isPartial = false;		
		TreeHierarchy.HierarchyNode node1, node2;		
													
				for(Dimension d : dims){
					//if(o1Obs.getDimensionLevel(d) < o2Obs.getDimensionLevel(d)) break; 
					node1 = hierarchy.getNode(obs1.getDimensionValue(d));
					node2 = hierarchy.getNode(obs2.getDimensionValue(d));	
					if(obs2.getDimensionValue(d)==null || obs2.getDimensionValue(d).equals("top")
							|| obs1.getDimensionValue(d)==null || obs1.getDimensionValue(d).equals("top")){
					/*if(obs2.getDimensionValue(d)==null 
							|| obs1.getDimensionValue(d)==null || obs2.getDimensionLevel(d)<=0){*/
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
			
		if(isPartial) {
			computedPartial++;
			partialContainments[methodIndex]++;
		}
		return isPartial;
		
	}
	
	public static void getGraphStatistics(){
		
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
			TreeHierarchy.HierarchyNode node = hierarchy.insertIntoSet(rs.get("parent").toString());
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
		
		String query;						
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
		
		System.out.println("Total observations: " + obs.size());
		long elapsedTime = System.nanoTime() - start;
		System.out.println("Preprocessing - Elapsed time: " + elapsedTime);
		start = System.nanoTime();
		long totes = 0;
		
		int total = 0, c = 0;
		computed = 0;
				
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
				
				if(cont==dims.size()) {
																					
						computeContainmentNaive(graph, obs1, obs2, 2);													
				}
			}
			
		}
			
			
		//System.out.println(total + " containment comparisons between cubes to be done.");
		System.out.println("Computed full: " + computed);		
		//System.out.println("Total comparisons: " + totes);
		elapsedTime = System.nanoTime() - start;
		System.out.println("FULL CONTAINMENT Elapsed time: " + elapsedTime);
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
					computePartialContainmentNaive(graph, obs1, obs2, 2);
				}				
			}
		
		}
		//System.out.println(total + " containment comparisons between cubes to be done.");
		//System.out.println("Computed full: " + computed);
		System.out.println("Computed partial: " + computedPartial);
		//System.out.println("Total comparisons: " + totes);
		elapsedTime = System.nanoTime() - start;
		System.out.println("PARTIAL CONTAINMENT: Elapsed time: " + elapsedTime);
		/*partialContainments[1] = computedPartial;
		fullContainments[1] = computed;*/
		
	}
	
	
	public static void createHashMapClustering(){
		//System.out.println(valueIndexMap.size());								
		
		int success = 0, fail = 0;		
		//Dataset dataset = new DefaultDataset();
		FastVector fvWekaAttributes = new FastVector(valueIndexMap.size());
		for(String value : valueIndexMap.keySet()){
			Attribute att = new Attribute(value);
			fvWekaAttributes.addElement(att);
		}
		Instances wekaD = new Instances("weka dataset", fvWekaAttributes, 0);
		Instances wekaSubD = new Instances("weka sub dataset", fvWekaAttributes, 0);
		ArrayList<Dimension> allDims = DimensionFactory.getInstance().getDimensions();
		int obsIndex = 0;
		int modCount = 0;
		Instance t1 = null, t2 = null;
		for(String obsKey : obs.keySet()){
			indexValueMap.put(obsIndex++, obsKey);
			Observation o = obs.get(obsKey);
			if(o.toString().equals("")) continue;
			//System.out.println(o.toString());
			dims = o.getDimensions();								
			//Instance obsInstance = new SparseInstance();
			double[] v = new double[valueIndexMap.size()];
			for(Dimension curDim : allDims){
				if(!dims.contains(curDim)){
					o.setDimensionLevel(curDim, -1);
					o.setDimensionValue(curDim, "http://www.imis.athena-innovation.gr/def#TopConcept");
				}				
				int index = valueIndexMap.get(o.getDimensionValue(curDim));
				//obsInstance.put(index, 1.0);
				v[index] = 1.0;
				try{
					//System.out.println(hierarchy.getNode(o.getDimensionValue(curDim)).toString());
					ArrayList<String> parents = hierarchy.getNode(o.getDimensionValue(curDim)).getParents();
					if(parents==null) {
						fail++;
						//System.out.println("FAIL: " + o.getDimensionValue(curDim));
						continue;
					}
					//System.out.println("SUCCESS " + o.getDimensionValue(curDim) + " parents: " + parents.toString());
					int weighted = parents.size();
					for(String parent : parents){
						index = valueIndexMap.get(parent);
						//obsInstance.put(index, 1.0);
						v[index] = Math.min(1.0, new Double(1.0/weighted));
						//v[index] = 1.0;
					}
					success++;
				}catch(Exception e){
					//e.printStackTrace();
				}
				
			}
			Instance sp = new Instance(1, v);
			/*if(obsIndex==1) t1 = sp;
			else if(obsIndex==2) t2 = sp;*/
			sp = new weka.core.SparseInstance(sp);
			wekaD.add(sp);
			if(modCount++ % 10 ==0 ) {
				wekaSubD.add(sp);
				//System.out.println(sp.toString());
			}
			
			//dataset.add(obsInstance);			
		}
		JaccardDistance jd = new JaccardDistance();
		//System.out.println("Distance is " + jd.distance(t1, t2));
		//System.out.println("Dataset size: " + dataset.size());
		System.out.println("Success: " + success);
		System.out.println("Fail: " + fail);
 		//LatentSemanticAnalysis 
				
		System.out.println("Weka dataset: " + wekaD.numInstances());
		System.out.println("WekaSub dataset: " + wekaSubD.numInstances());
		
		start = System.nanoTime();
		//if(true) return;		
		 
		/*Clusterer km = new KMeans(numberOfClusters);
		System.out.println("Sampling...");
		Sampling s=Sampling.SubSampling;
		Pair<Dataset, Dataset> datas=s.sample(dataset, (int)(dataset.size()*0.1));
		System.out.println("Done sampling.");*/
		numberOfClusters = (int) Math.sqrt(wekaD.numInstances()/2);
		String[] options = new String[]{
				"-N",
				""+numberOfClusters
				/*"-D",
				"weka.core.ChebyshevDistance"*/
		};
		 /*options[0] = "-N";                 // max. iterations
		 options[1] = ""+numberOfClusters;
		 options[2] = "-D";
		 options[3] = "weka.core.ChebyshevDistance";*/		
		 try{			 
			 if(featSelect){
				 System.out.println("Applying attribute selection.....");
				 AttributeSelection filter = new AttributeSelection(); // package weka.filters.supervised.attribute!
				 LatentSemanticAnalysis lsa = new LatentSemanticAnalysis();				 				
				 Ranker rank = new Ranker();
				 filter.setEvaluator(lsa);
				 filter.setSearch(rank);
				 filter.setInputFormat(wekaSubD);
				 wekaSubD = Filter.useFilter(wekaSubD, filter);
				 wekaD = Filter.useFilter(wekaD, filter);
			 }			 
			 
			 			
			 ClusterEvaluation eval = new ClusterEvaluation();
			 //DBSCAN clusterer = new DBSCAN();
			 //clusterer.setOptions(options);
			 //CLOPE clusterer = new CLOPE(); //26% se 500-10
			 
			 //weka.clusterers.forOPTICSAndDBScan.DataObjects.
			 /*FarthestFirst clusterer = new FarthestFirst(); //71% 500-10, 74% 1000-10
			 clusterer.setNumClusters(numberOfClusters/8);*/
			 XMeans clusterer = new XMeans();			 
			 //HierarchicalClusterer clusterer = new HierarchicalClusterer();
			 /*HierarchicalClusterer hc = new HierarchicalClusterer();
			 hc.*/
			 //clusterer.setPreserveInstancesOrder(true);
			 //clusterer.setNumClusters(numberOfClusters/4);
			 //clusterer.setMaxIterations(20);
			 clusterer.setMinNumClusters(Math.max(1, numberOfClusters/4));
			 clusterer.setMaxNumClusters(numberOfClusters*2);
			 //clusterer.setDistanceF(new ChebyshevDistance());
			 //clusterer.setUseKDTree(true);
			 //
			 //clusterer.setDistanceF(new ManhattanDistance());
			 clusterer.setDistanceF(new JaccardDistance());
			 //clusterer.set
			 //clusterer.setSeed(10);
			 
			 //clusterer.se
			 //kmeansClusterer.setOptions(options);     // set the options
			 //clusterer.buildClusterer(wekaD);    // build the clusterer
			 //clusterer.clusterInstance(arg0)
			 clusterer.buildClusterer(wekaSubD);    // build the clusterer
			 /*System.out.println("Epsilon: " + clusterer.getEpsilon());
			 System.out.println("MinPoints: " + clusterer.getMinPoints());
			 clusterer.setEpsilon(10);
			 clusterer.setMinPoints(10000);*/
			 eval.setClusterer(clusterer);
			 eval.evaluateClusterer(wekaD);
			 System.out.println("# of clusters: " + eval.getNumClusters());
			 System.out.println(eval.clusterResultsToString());
			/* Enumeration<Instance> en = wekaD.enumerateInstances();
			 HashMap<Instance, Integer> instanceIndexMap = new HashMap<Instance, Integer>();
			 int obs_ind=0;
			 while(en.hasMoreElements()){
				 Instance next = en.nextElement();
				 instanceIndexMap.put(next, obs_ind);
				 obs_ind++;
			 }
			 HashMap<Integer, HashSet<Instance>> hierarchicalMap = new HashMap<Integer, HashSet<Instance>>();		
			 System.out.println(obs_ind);
			 obs_ind = 0;
			 en = wekaD.enumerateInstances();
			 computed = 0;
			 
			 while(en.hasMoreElements()){
				 Instance next = en.nextElement();
				 int nextind = clusterer.clusterInstance(next);
				 if(hierarchicalMap.containsKey(nextind)){
					 for(Instance sin : hierarchicalMap.get(nextind)){
											 
						 Observation obs1 = obs.get(indexValueMap.get(obs_ind));
						 Observation obs2 = obs.get(indexValueMap.get(instanceIndexMap.get(sin)));
						 //System.out.println(obs1 + " " + obs2);
						 computeContainmentNaive(graph, obs1, obs2, 1);
											 
					 }
						 hierarchicalMap.get(nextind).add(next);
					 
				 }
					 
				 else{
					 
					 HashSet<Instance> s = new HashSet<Instance>();					 					 
					 s.add(next);					 
					 hierarchicalMap.put(nextind, s);
					 
				 }					 
				 obs_ind++;
			 }
			 System.out.println(obs_ind);
			 while(en.hasMoreElements()){
				 Instance next = en.nextElement();
				 int nextind = clusterer.clusterInstance(next);
				 
			 }
			 System.out.println("Computed full: " +computed);
			 long elapsedTime = System.nanoTime() - start;
			 System.out.println("FULL containment - Elapsed time: " + elapsedTime);
			 
			 if(true) return;*/
			 //System.out.println(eval.clusterResultsToString());
			 double[] assignments = eval.getClusterAssignments();
			 System.out.println(assignments.length);
			 double cluster;
			 HashMap<String, Double> clusterMap = new HashMap<String, Double>();
			 for(int i = 0; i < assignments.length; i++){
				 cluster= assignments[i];
				 clusterMap.put(indexValueMap.get(i), cluster);
			 }
			 System.out.println("Cluster map size : " + clusterMap.size());
			 long elapsedTime = System.nanoTime() - start;
				System.out.println("Preprocessing - Elapsed time: " + elapsedTime);
			 Instances centers = clusterer.getClusterCenters();
			/* System.out.println("Canopy Cluster map size : " + clusterMap.size());
			 elapsedTime = System.nanoTime() - start;
			 System.out.println("Canopy Clustering - Elapsed time: " + elapsedTime);*/
			 computed = 0;
			 computedPartial = 0;
			 start = System.nanoTime();
			 int clusterIndex;
			 int comparisons = 0;
			 int containmentComputations = 0;
			 for(String obs1 : clusterMap.keySet()){
				  clusterIndex = clusterMap.get(obs1).intValue();
				  for(String obs2 : clusterMap.keySet()){
					  comparisons++;
					int clusterIndex2 = clusterMap.get(obs2).intValue();
					if(clusterIndex != clusterIndex2) continue;
					computeContainmentNaive(graph, obs.get(obs1), obs.get(obs2), 1);
					containmentComputations++;
				  }
			 }
			 System.out.println("Total comparisons: " +comparisons + ", containment computations: " + containmentComputations);
			 System.out.println("Computed full: " +computed);
			 elapsedTime = System.nanoTime() - start;
			 System.out.println("FULL containment - Elapsed time: " + elapsedTime);
			 start = System.nanoTime();
			 
			 JaccardDistance md = new JaccardDistance(wekaD);
			 double min = Double.MAX_VALUE, max = Double.MIN_VALUE, dist = 0;;
			 int partComp = 0;
			 
			 for(String obs1 : clusterMap.keySet()){
				  clusterIndex = clusterMap.get(obs1).intValue();
				  Instance center1 = centers.instance(clusterIndex);
				  for(String obs2 : clusterMap.keySet()){
					int clusterIndex2 = clusterMap.get(obs2).intValue();
					Instance center2 = centers.instance(clusterIndex2);
					if(clusterIndex != clusterIndex2) continue;		
					//dist = md.distance(center1, center2);
					//if(dist > 7.5) continue;
					partComp++;
					int cont = 0, cont_rev = 0;
					for(Dimension d : dims){
						
						if(obs.get(obs1).getDimensionLevel(d)>obs.get(obs2).getDimensionLevel(d)) {				
							cont++;						
							break;
						}					
					}				
					if(cont>0)					
						if(computePartialContainmentNaive(graph, obs.get(obs1), obs.get(obs2), 1)){
							min = Math.min(min, dist );
							max = Math.max(max, dist );							
							/*//System.out.println("Centers: " + center1 + ", " + center2);
							*/
						}
				  }
			 }
			 System.out.println("Computed partial: " +computedPartial);
			 System.out.println("Comparisons partial: " +partComp);
			 System.out.println("Max distance: " + max + ", Min distance: " + min);
			 
			 elapsedTime = System.nanoTime() - start;
			 System.out.println("PARTIAL containment - Elapsed time: " + elapsedTime);
			 start = System.nanoTime();
			 
			/* ClusterEvaluation eval2 = new ClusterEvaluation();
			 SimpleKMeans kmeansClusterer = new SimpleKMeans();
			 kmeansClusterer.setPreserveInstancesOrder(true);
			 kmeansClusterer.setNumClusters(numberOfClusters);
			 //kmeansClusterer.setOptions(options);     // set the options
			 kmeansClusterer.buildClusterer(wekaD);    // build the clusterer
			 eval2.setClusterer(kmeansClusterer);  
			 eval2.evaluateClusterer(wekaD); 
			 System.out.println("# of clusters: " + eval2.getNumClusters());
			 //System.out.println(eval.clusterResultsToString());
			 double[] assignmentsKMeans = eval2.getClusterAssignments();
			 System.out.println(assignments.length);
			 double clusterKmeans;
			 HashMap<String, Double> clusterMapKMeans = new HashMap<String, Double>();
			 for(int i = 0; i < assignmentsKMeans.length; i++){
				 clusterKmeans = assignmentsKMeans[i]; 
				 clusterMapKMeans.put(indexValueMap.get(i), clusterKmeans);
			 }


			 System.out.println("KMeans Cluster map size : " + clusterMapKMeans.size());
			 elapsedTime = System.nanoTime() - start;
			 System.out.println("KMeans Clustering - Elapsed time: " + elapsedTime);
			 			 		
			 computed = 0;
			 computedPartial = 0;
			 start = System.nanoTime();

			 for(String obs1 : clusterMapKMeans.keySet()){
				  clusterIndex = clusterMapKMeans.get(obs1).intValue();
				  for(String obs2 : clusterMapKMeans.keySet()){
					int clusterIndex2 = clusterMapKMeans.get(obs2).intValue();
					if(clusterIndex != clusterIndex2) continue;
					//computeContainmentNaive(graph, obs.get(obs1), obs.get(obs2));
					int cont = 0, cont_rev = 0;
					for(Dimension d : dims){
						
						if(obs.get(obs1).getDimensionLevel(d)>obs.get(obs2).getDimensionLevel(d)) {				
							cont++;						
							break;
						}					
					}				
					if(cont>0)					
						computePartialContainmentNaive(graph, obs.get(obs1), obs.get(obs2), 1);
				  }
			 }
			 System.out.println("Computed full: " +computed);
			 elapsedTime = System.nanoTime() - start;
			 System.out.println("FULL containment - Elapsed time: " + elapsedTime);
			 start = System.nanoTime();
			 
			 for(String obs1 : clusterMapKMeans.keySet()){
				  clusterIndex = clusterMapKMeans.get(obs1).intValue();
				  for(String obs2 : clusterMapKMeans.keySet()){
					int clusterIndex2 = clusterMapKMeans.get(obs2).intValue();
					if(clusterIndex != clusterIndex2) continue;
					computeContainmentNaive(graph, obs.get(obs1), obs.get(obs2));
				  }
			 }
			 System.out.println("Computed partial: " +computedPartial);
			 elapsedTime = System.nanoTime() - start;
			 System.out.println("PARTIAL containment - Elapsed time: " + elapsedTime);
			 start = System.nanoTime();
			 partialContainments[2] = computedPartial;
			 fullContainments[2] = computed;*/
			 //System.out.println(Arrays.toString(assignments));
			 //canopy.
			/* System.out.println(clusterer.getNumClusters());
			 System.out.println(clusterer.getMinimumCanopyDensity());
			 Iterator<weka.core.Instance> it = clusterer.getCanopies().iterator();
			 while(it.hasNext()) {
				
			 }*/
			 //System.out.println(canopy.);
			 /*ClusterEvaluation eval = new ClusterEvaluation();
			 eval.setClusterer(canopy);                                   // the cluster to evaluate
			 eval.evaluateClusterer();                                // data to evaluate the clusterer on
			 System.out.println("# of clusters: " + eval.getNumClusters());  // output # of clusters
*/		 }catch(Exception e){
			 e.printStackTrace();
		 }
		 
	
	}
	
}

