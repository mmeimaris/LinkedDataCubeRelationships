package models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

public class Observation {

		
	HashMap<Dimension, Integer> levels = new HashMap<Dimension, Integer>();
	HashMap<Dimension, String> values = new HashMap<Dimension, String>();	
	String uri;
	
	public Observation(String uri){
		this.uri = uri;
	}
	
	public void setDimensionLevel(Dimension dimension, int level){
		levels.put(dimension, level);
	}
	
	public void setDimensionValue(Dimension dimension, String value){
		values.put(dimension, value);
	}
	
	public String getDimensionValue(Dimension dimension){
		return values.get(dimension);
	}
	
	public int getDimensionLevel(Dimension dimension){
		return levels.get(dimension);
	}
	
	public Set getDimensions(){
		return levels.keySet();
	}
	
	@Override
	public boolean equals(Object other){		
		return toString().equals(other.toString());
	}
	
	@Override
	public int hashCode(){
		Set<Dimension> dimensions = levels.keySet();
		ArrayList<Dimension> list = new ArrayList<Dimension>(dimensions);
		Collections.sort(list);
		StringBuilder builder = new StringBuilder();
		for(Dimension dim : list){
			try{
				builder.append(dim.getRepresentative()).append("_").append(levels.get(dim));
			}catch(NullPointerException e){
				continue;
			}
		}
		if(dimensions.isEmpty()) return 0;
		return builder.toString().hashCode();
		
	}
	
	public String toString(){
			
		Set<Dimension> dimensions = levels.keySet();
		ArrayList<Dimension> list = new ArrayList<Dimension>(dimensions);
		Collections.sort(list);
		StringBuilder builder = new StringBuilder();
		for(Dimension dim : list){
			try{
				builder.append(dim.getRepresentative()).append("_").append(levels.get(dim))
						.append("(").append(values.get(dim)).append(")");
				//builder.append(dim.getRepresentative()).append("(").append(values.get(dim)).append(")");
			}catch(NullPointerException e){
				continue;
			}
		}
		if(dimensions.isEmpty()) return "";
		return builder.toString();
		
	}
	
}
