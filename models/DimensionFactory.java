package models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class DimensionFactory {

	private static DimensionFactory factory = new DimensionFactory();
	private static ArrayList<Dimension> dimensions;
	private static HashSet<Dimension> dimensionSet;
	private static HashMap<String, Dimension> dimensionMap;
	private static HashMap<String, Dimension> representativeMap;
	private DimensionFactory(){
		dimensions = new ArrayList<Dimension>();
		dimensionSet = new HashSet<Dimension>();
		dimensionMap = new HashMap<String, Dimension>();
		representativeMap = new HashMap<String, Dimension>();
		setDimensions();
	}
		
	public static DimensionFactory getInstance(){
		return factory;
	}
	
	public  ArrayList<Dimension> getDimensions(){
		return this.dimensions;
	}
	
	private void setDimensions(){	
		String prefix = "http://imis.athena-innovation.gr/def#";
		Dimension sex = new Dimension();
		HashSet<String> sexes = new HashSet<String>();
		sexes.add("http://purl.org/linked-data/sdmx/2009/dimension#sex");
		sexes.add("http://ontologycentral.com/2009/01/eurostat/ns#sex");
		sex.setMembers(sexes);
		sex.setRepresentative("sex");
		this.dimensions.add(sex);
		dimensionSet.add(sex);
		dimensionMap.put("http://purl.org/linked-data/sdmx/2009/dimension#sex", sex);
		dimensionMap.put("http://ontologycentral.com/2009/01/eurostat/ns#sex", sex);
		representativeMap.put(prefix+"sex", sex);
		
		Dimension date = new Dimension();
		HashSet<String> dates = new HashSet<String>();
		dates.add("http://purl.org/linked-data/sdmx/2009/dimension#refPeriod");
		dates.add("http://purl.org/dc/terms/date");
		date.setMembers(dates);
		date.setRepresentative("time");
		this.dimensions.add(date);
		dimensionSet.add(date);
		dimensionMap.put("http://purl.org/linked-data/sdmx/2009/dimension#refPeriod", date);
		dimensionMap.put("http://purl.org/dc/terms/date", date);
		representativeMap.put(prefix+"time", date);
		
		Dimension location = new Dimension();
		HashSet<String> locations = new HashSet<String>();
		locations.add("http://purl.org/linked-data/sdmx/2009/dimension#refArea");
		locations.add("http://ontologycentral.com/2009/01/eurostat/ns#geo");
		location.setMembers(locations);
		location.setRepresentative("location");
		this.dimensions.add(location);
		dimensionSet.add(location);
		dimensionMap.put("http://purl.org/linked-data/sdmx/2009/dimension#refArea", location);
		dimensionMap.put("http://ontologycentral.com/2009/01/eurostat/ns#geo", location);
		representativeMap.put(prefix+"location", location);
		
		Dimension age = new Dimension();
		HashSet<String> ages = new HashSet<String>();
		ages.add("http://purl.org/linked-data/sdmx/2009/dimension#age");
		ages.add("http://ontologycentral.com/2009/01/eurostat/ns#age");
		age.setMembers(ages);
		age.setRepresentative("age");
		this.dimensions.add(age);
		dimensionSet.add(age);
		dimensionMap.put("http://purl.org/linked-data/sdmx/2009/dimension#age", age);
		dimensionMap.put("http://ontologycentral.com/2009/01/eurostat/ns#age", age);
		representativeMap.put(prefix+"age", age);
		
		Dimension educ = new Dimension();
		HashSet<String> educs = new HashSet<String>();
		educs.add("http://purl.org/linked-data/sdmx/2009/dimension#educationLev");		
		educ.setMembers(educs);
		educ.setRepresentative("education_level");
		this.dimensions.add(educ);
		dimensionSet.add(educ);
		dimensionMap.put("http://purl.org/linked-data/sdmx/2009/dimension#educationLev", educ);
		representativeMap.put(prefix+"educ", educ);
		
		Dimension place_of_residence = new Dimension();
		HashSet<String> place_of_residences = new HashSet<String>();
		place_of_residences.add("http://linked-statistics.gr/ontology/dimension/place-of-residence");		
		place_of_residence.setMembers(place_of_residences);
		place_of_residence.setRepresentative("place_of_residence");				
		this.dimensions.add(place_of_residence);
		dimensionSet.add(place_of_residence);
		dimensionMap.put("http://linked-statistics.gr/ontology/dimension/place-of-residence", place_of_residence);		
		representativeMap.put(prefix+"place_of_residence", place_of_residence);
		
		Dimension household_size = new Dimension();
		HashSet<String> household_sizes = new HashSet<String>();
		household_sizes.add("http://linked-statistics.gr/ontology/dimension/householdSize");		
		household_size.setMembers(household_sizes);
		household_size.setRepresentative("household_size");				
		this.dimensions.add(household_size);
		dimensionSet.add(household_size);
		dimensionMap.put("http://linked-statistics.gr/ontology/dimension/householdSize", household_size);
		representativeMap.put(prefix+"household_size", household_size);
		
		Dimension citizenship = new Dimension();
		HashSet<String> citizenships = new HashSet<String>();
		citizenships.add("http://linked-statistics.gr/ontology/dimension/citizenship");		
		citizenship.setMembers(citizenships);
		citizenship.setRepresentative("citizenship");				
		this.dimensions.add(citizenship);
		dimensionSet.add(citizenship);
		dimensionMap.put("http://linked-statistics.gr/ontology/dimension/citizenship", citizenship);
		representativeMap.put(prefix+"citizenship", citizenship);
		
		Dimension nace = new Dimension();
		HashSet<String> naces = new HashSet<String>();
		naces.add("http://ontologycentral.com/2009/01/eurostat/ns#nace_r1");		
		nace.setMembers(naces);
		nace.setRepresentative("nace");				
		this.dimensions.add(nace);
		dimensionSet.add(nace);
		dimensionMap.put("http://ontologycentral.com/2009/01/eurostat/ns#nace_r1", nace);
		representativeMap.put(prefix+"nace", nace);
		
		
		Dimension currency = new Dimension();
		HashSet<String> currencies = new HashSet<String>();
		currencies.add("http://ontologycentral.com/2009/01/eurostat/ns#currency");		
		currency.setMembers(currencies);
		currency.setRepresentative("currency");				
		this.dimensions.add(currency);
		dimensionSet.add(currency);
		dimensionMap.put("http://ontologycentral.com/2009/01/eurostat/ns#currency", currency);
		representativeMap.put(prefix+"currency", currency);
		
		Dimension unit = new Dimension();
		HashSet<String> units = new HashSet<String>();
		units.add("http://ontologycentral.com/2009/01/eurostat/ns#unit");		
		unit.setMembers(units);
		unit.setRepresentative("unit");				
		this.dimensions.add(unit);
		dimensionSet.add(unit);
		dimensionMap.put("http://ontologycentral.com/2009/01/eurostat/ns#unit", unit);
		representativeMap.put(prefix+"unit", unit);
										
	}
	
	public Dimension getDimension(String uri){
		return dimensionMap.get(uri);
	}
	public Dimension getDimensionByRepresentative(String rep){
		return representativeMap.get(rep);
	}
}
