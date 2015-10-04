package tests;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;

import models.Dimension;
import models.DimensionFactory;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

import virtuoso.jena.driver.VirtGraph;
import virtuoso.jena.driver.VirtuosoQueryExecution;
import virtuoso.jena.driver.VirtuosoQueryExecutionFactory;
import virtuoso.jena.driver.VirtuosoUpdateFactory;
import virtuoso.jena.driver.VirtuosoUpdateRequest;
import app.DataConnection;

import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.vocabulary.OWL;

public class DataCleaner {
	String prefix = "http://imis.athena-innovation.gr/def#";
	String codePrefix ="http://imis.athena-innovation.gr/code#"; 
	
	public void fixTimes(String namedGraph){
		VirtGraph graph = DataConnection.getConnection();
		String query = "INSERT INTO <"+namedGraph+"> {?s imis:time ?time }" +
				" WHERE {{SELECT ?s ?time FROM <"+namedGraph+"> WHERE {?s imis:time ?lex BIND (iri(bif:concat(\"http://imis.athena-innovation.gr/code#\",?lex)) AS ?time)}}}" ;
		System.out.println(query);
		VirtuosoUpdateRequest qur = VirtuosoUpdateFactory.create(query, graph);
		qur.exec(); 
		graph.close();
	}
	
	public void normalizeDataset(String namedGraph){
		VirtGraph graph = DataConnection.getConnection();
		ArrayList<Dimension> dimensions = DimensionFactory.getInstance().getDimensions();
		for(Dimension d : dimensions){			
			HashSet<String> members = d.getMembers();
			for(String member : members){
				String query = "INSERT INTO <"+namedGraph+"> " +
						"{" +
							" ?obs <"+prefix+d.getRepresentative()+"> ?o" +
						"}" +
						"WHERE " +
						"{" +
							"{" +
								" SELECT ?obs ?o FROM <"+namedGraph+"> WHERE {" +
									"?obs <"+member+"> ?o " +
							"}}" +
						"}"		;
				VirtuosoUpdateRequest qur = VirtuosoUpdateFactory.create(query, graph);
				qur.exec(); 		
				query = "DELETE FROM <"+namedGraph+"> " +
						"{" +
							" ?obs <"+member+"> ?o" +
						"}" +
						"WHERE " +
						"{" +
							"{" +
								" SELECT ?obs ?o FROM <"+namedGraph+"> WHERE {" +
									"?obs <"+member+"> ?o " +
							"}}" +
						"}"		;
				qur = VirtuosoUpdateFactory.create(query, graph);
				qur.exec(); 
			}				
		}
		graph.close();				
	}
	
	public void normalizeHierarchies(Dimension d, HashSet<String> graphs){
		
		VirtGraph graph = DataConnection.getConnection();
		//ArrayList<Dimension> dimensions = DimensionFactory.getInstance().getDimensions();
		Model model = ModelFactory.createDefaultModel();
		for(String gr : graphs){	
			System.out.println(gr);			
			String rep = prefix+d.getRepresentative();
			String query = "SELECT distinct ?v FROM <"+gr+"> WHERE {" +
						"?obs <"+rep+"> ?v " + 
					"}";
			VirtuosoQueryExecution vqe = VirtuosoQueryExecutionFactory.create (query, graph);
			ResultSet results = vqe.execSelect();			
			while(results.hasNext()){				
				//System.out.println(results.getRowNumber());
				QuerySolution rs = results.next();				
				RDFNode value = rs.get("v");
				String newVal = "";;
				if(value.isResource()){
					if(value.toString().lastIndexOf("#") > -1){
						newVal = codePrefix + value.toString().replace("sex-", "").substring(value.toString().lastIndexOf("#")+1);
					}
					else if(value.toString().lastIndexOf("/") > -1){
						newVal = codePrefix + value.toString().substring(value.toString().lastIndexOf("/")+1);
					}
					else System.out.println("No last index for " + value.toString());
				}
				else {
					newVal = codePrefix + value.toString();
				}				
				model.createResource(newVal).addProperty(OWL.sameAs, ResourceFactory.createResource(value.toString()));
			}
			vqe.close();					
		}
		try {
			FileOutputStream fos = new FileOutputStream("C:/Users/Marios/Documents/Projects/Multidimensional Similarity/Codelists/"+d.getRepresentative()+".rdf");
			RDFDataMgr.write(fos, model, Lang.TURTLE) ;
			fos.close();
		} catch (Exception e) {				
			e.printStackTrace();
		}		
		model.close();

		
	}

	
public void normalizeExistingCodelists(){
		
		VirtGraph graph = DataConnection.getConnection();
		//ArrayList<Dimension> dimensions = DimensionFactory.getInstance().getDimensions();
			
		String[] codelists = new String[]{"codelists.age", "codelists.sex", "codelists.location", "codelists.admin.divisions"};
		for(String codelist : codelists){
				Model model = ModelFactory.createDefaultModel();		
				String query = "SELECT distinct ?v FROM <"+codelist+"> WHERE {" +
							"?v a skos:Concept " + 
						"}";				
				VirtuosoQueryExecution vqe = VirtuosoQueryExecutionFactory.create (query, graph);
				ResultSet results = vqe.execSelect();			
				while(results.hasNext()){
					//System.out.println(results.getRowNumber());
					QuerySolution rs = results.next();
					RDFNode value = rs.get("v");
					String newVal = "";;
					if(value.isResource()){
						if(value.toString().lastIndexOf("#") > -1){
							newVal = codePrefix + value.toString().replace("sex-", "").substring(value.toString().lastIndexOf("#")+1);
						}
						else if(value.toString().lastIndexOf("/") > -1){
							newVal = codePrefix + value.toString().substring(value.toString().lastIndexOf("/")+1);
						}
						else System.out.println("No last index for " + value.toString());
					}
					else {
						newVal = codePrefix + value.toString();
					}				
					model.createResource(newVal).addProperty(OWL.sameAs, ResourceFactory.createResource(value.toString()));
				}
				vqe.close();					
			
			try {
				FileOutputStream fos = new FileOutputStream("C:/Users/Marios/Documents/Projects/Multidimensional Similarity/Codelists/"+codelist+".rdf");
				RDFDataMgr.write(fos, model, Lang.TURTLE) ;
				fos.close();
			} catch (Exception e) {				
				e.printStackTrace();
			}		
			model.close();
		}
		graph.close();
	}

	public void discoverHierarchies(String codelist){
		VirtGraph graph = DataConnection.getConnection();
		Model model = ModelFactory.createDefaultModel();			
		String query = "select distinct ?s ?notation from <"+codelist+"> where { " +
				"?s a skos:Concept ; skos:notation ?notation}";
		VirtuosoQueryExecution vqe = VirtuosoQueryExecutionFactory.create (query, graph);
		ResultSet results = vqe.execSelect();			
		while(results.hasNext()){							
			QuerySolution rs = results.next();
			RDFNode s = rs.get("s");
			String val = rs.get("notation").toString();
			if(val.contains("-")){
				val = val.replace("Y", "");
				String[] range = val.split("-");
				int min = Integer.parseInt(range[0]);
				int max = Integer.parseInt(range[1]);
				for(int i = min ; i<=max ; i++){
					Resource code = model.createResource("http://linked-statistics.gr/ontology/code/2011/age/Y"+i);
					code.addProperty(ResourceFactory.createProperty("http://www.w3.org/2004/02/skos/core#broaderTransitive"), s.asResource());
				}
			}
			else if(val.contains("Y_GE")){
				val = val.replace("Y_GE", "");				
				int min = Integer.parseInt(val);				
				for(int i = min ; i<=120 ; i++){
					Resource code = model.createResource("http://linked-statistics.gr/ontology/code/2011/age/Y"+i);
					code.addProperty(ResourceFactory.createProperty("http://www.w3.org/2004/02/skos/core#broaderTransitive"), s.asResource());
				}
			}
			else if(val.contains("Y_LT")){
				val = val.replace("Y_LT", "");				
				int max = Integer.parseInt(val);				
				for(int i = 0 ; i<=max ; i++){
					Resource code = model.createResource("http://linked-statistics.gr/ontology/code/2011/age/Y"+i);
					code.addProperty(ResourceFactory.createProperty("http://www.w3.org/2004/02/skos/core#broaderTransitive"), s.asResource());
				}
			}
			
		}
		vqe.close();
		try {
			FileOutputStream fos = new FileOutputStream("C:/Users/Marios/Documents/Projects/Multidimensional Similarity/Codelists/enriched_"+codelist+".rdf");
			RDFDataMgr.write(fos, model, Lang.TURTLE) ;
			fos.close();
		} catch (Exception e) {				
			e.printStackTrace();
		}		
		model.close();
		graph.close();
	}
	
	public void sameLocations(){
		
		VirtGraph graph = DataConnection.getConnection();
		Model model = ModelFactory.createDefaultModel();
		String codelist = "codelists.admin.divisions";
		String query = "SELECT distinct ?s ?v FROM <"+codelist+"> WHERE {" +
					"?s owl:sameAs ?v FILTER regex(iri(?v), \"nuts2008\")}";					
		VirtuosoQueryExecution vqe = VirtuosoQueryExecutionFactory.create (query, graph);
		ResultSet results = vqe.execSelect();			
		while(results.hasNext()){							
			QuerySolution rs = results.next();
			RDFNode v = rs.get("v");
			RDFNode s = rs.get("s");
			
		}
		vqe.close();
	}
}
