package models;

import java.util.ArrayList;
import java.util.HashMap;

public class SimpleCube {
	
	HashMap<Dimension, Integer> levels = new HashMap<Dimension, Integer>();
	public SimpleCube(ArrayList<Dimension> dimensions){
					
	}
	
	public void setDimensionLevel(Dimension dimension, int level){
		levels.put(dimension, level);
	}
	
	public int getDimensionLevel(Dimension dimension){
		return levels.get(dimension);
	}
	
	

}
