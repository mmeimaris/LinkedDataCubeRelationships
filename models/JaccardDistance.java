package adasdasd;

import java.util.BitSet;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Vector;

import weka.core.Instance;
import weka.core.Instances;
import weka.core.NormalizableDistance;
import weka.core.Option;
import weka.core.RevisionUtils;
import weka.core.TechnicalInformation;
import weka.core.TechnicalInformation.Field;
import weka.core.TechnicalInformation.Type;
import weka.core.TechnicalInformationHandler;
import weka.core.Utils;
import weka.core.neighboursearch.PerformanceStats;


public class JaccardDistance extends NormalizableDistance implements
  Cloneable, TechnicalInformationHandler {

  /** for serialization. */
  private static final long serialVersionUID = -7446019339455453893L;


  /**
   * Constructs an Minkowski Distance object, Instances must be still set.
   */
  public JaccardDistance() {
    super();
  }

  /**
   * Constructs an Minkowski Distance object and automatically initializes the
   * ranges.
   * 
   * @param data the instances the distance function should work on
   */
  public JaccardDistance(Instances data) {
    super(data);
  }

  /**
   * Returns a string describing this object.
   * 
   * @return a description of the evaluator suitable for displaying in the
   *         explorer/experimenter gui
   */
  @Override
  public String globalInfo() {
    return "Implementing Minkowski distance (or similarity) function.\n\n"
      + "One object defines not one distance but the data model in which "
      + "the distances between objects of that data model can be computed.\n\n"
      + "Attention: For efficiency reasons the use of consistency checks "
      + "(like are the data models of the two instances exactly the same), "
      + "is low.\n\n" + "For more information, see:\n\n"
      + getTechnicalInformation().toString();
  }

  /**
   * Returns an instance of a TechnicalInformation object, containing detailed
   * information about the technical background of this class, e.g., paper
   * reference or book this class is based on.
   * 
   * @return the technical information about this class
   */
  @Override
  public TechnicalInformation getTechnicalInformation() {
    TechnicalInformation result;

    result = new TechnicalInformation(Type.MISC);
    result.setValue(Field.AUTHOR, "Wikipedia");
    result.setValue(Field.TITLE, "Minkowski distance");
    result.setValue(Field.URL,
      "http://en.wikipedia.org/wiki/Minkowski_distance");

    return result;
  }

  /**
   * Returns an enumeration describing the available options.
   * 
   * @return an enumeration of all the available options.
   */
  @Override
  public Enumeration<Option> listOptions() {
    Vector<Option> result = new Vector<Option>();

    result.addElement(new Option(
      "\tThe order 'p'. With '1' being the Manhattan distance and '2'\n"
        + "\tthe Euclidean distance.\n" + "\t(default: 2)", "P", 1,
      "-P <order>"));

    result.addAll(Collections.list(super.listOptions()));

    return result.elements();
  }





  /**
   * Returns the tip text for this property.
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String orderTipText() {
    return "The order of the Minkowski distance ('1' is Manhattan distance and "
      + "'2' the Euclidean distance).";
  }

  
  

  /**
   * Calculates the distance between two instances.
   * 
   * @param first the first instance
   * @param second the second instance
   * @return the distance between the two given instances
   */
  @Override
  public double distance(Instance first, Instance second) {
	  	BitSet bs1 = new BitSet();
	  	double[] v1 = first.toDoubleArray(), v2 = second.toDoubleArray();
		for(int i = 0; i < v1.length; i++){
			if(v1[i]==1) bs1.set(i, true);
		}
		BitSet bs2 = new BitSet();
		for(int i = 0; i < v2.length; i++){
			if(v2[i]==1) bs2.set(i, true);
		}
		BitSet bs3 = (BitSet) bs1.clone();
		bs1.and(bs2);		
		//System.out.println((bs1.cardinality()));
		bs3.or(bs2);
		//System.out.println((bs3.cardinality()));
		return (double) 1-(bs1.cardinality()/bs3.cardinality());///Math.max(bs3.cardinality(), bs2.cardinality()));
		
  }

  /**
   * Calculates the distance (or similarity) between two instances. Need to pass
   * this returned distance later on to postprocess method to set it on correct
   * scale. <br/>
   * P.S.: Please don't mix the use of this function with distance(Instance
   * first, Instance second), as that already does post processing. Please
   * consider passing Double.POSITIVE_INFINITY as the cutOffValue to this
   * function and then later on do the post processing on all the distances.
   * 
   * @param first the first instance
   * @param second the second instance
   * @param stats the structure for storing performance statistics.
   * @return the distance between the two given instances or
   *         Double.POSITIVE_INFINITY.
   *//*
  @Override
  public double distance(Instance first, Instance second, PerformanceStats stats) { // debug
                                                                                    // method
                                                                                    // pls
                                                                                    // remove
                                                                                    // after
                                                                                    // use
    return Math.pow(distance(first, second, Double.POSITIVE_INFINITY, stats),
      1 / m_Order);
  }*/

  /**
   * Updates the current distance calculated so far with the new difference
   * between two attributes. The difference between the attributes was
   * calculated with the difference(int,double,double) method.
   * 
   * @param currDist the current distance calculated so far
   * @param diff the difference between two new attributes
   * @return the update distance
   * @see #difference(int, double, double)
   */
  @Override
  protected double updateDistance(double currDist, double diff) {


    return currDist;
  }

  /**
   * Does post processing of the distances (if necessary) returned by
   * distance(distance(Instance first, Instance second, double cutOffValue). It
   * is necessary to do so to get the correct distances if
   * distance(distance(Instance first, Instance second, double cutOffValue) is
   * used. This is because that function actually returns the squared distance
   * to avoid inaccuracies arising from floating point comparison.
   * 
   * @param distances the distances to post-process
   */
  @Override
  public void postProcessDistances(double distances[]) {
    
  }

  /**
   * Returns the revision string.
   * 
   * @return the revision
   */
  @Override
  public String getRevision() {
    return RevisionUtils.extract("$Revision: 0$");
  }
}
