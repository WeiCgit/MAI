/**
 * PCAMeans is used to compress a vector using PCA and find the cluster who's
 * mean is nearest to this vector.
 */

package UvA.stateSpaceReduction;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.javaml.clustering.Clusterer;
import net.sf.javaml.clustering.KMeans;
import net.sf.javaml.core.Dataset;
import net.sf.javaml.core.DefaultDataset;
import net.sf.javaml.core.DenseInstance;
import net.sf.javaml.core.Instance;
import net.sf.javaml.distance.DistanceMeasure;
import net.sf.javaml.distance.EuclideanDistance;
import UvA.states.State;

import com.jmatio.io.MatFileWriter;
import com.jmatio.types.MLArray;
import com.jmatio.types.MLDouble;

public class PCAMeans implements Serializable
{
	private static final long serialVersionUID = -2478983458498184932L;
	
	private final int numComponents;
	private final int clusterAmount;
	private final int iterations;

	private PrincipleComponentAnalysis pca;
	private Dataset means;
	private Map<Instance, Integer> projectToMeanCache;	// cache contains previous conversions from projected vector (using PCA) to mean-index

	private static int verbose = 0;

	/**
	 * Constructor performs PCA on states and clusters the eigen space projections.
	 * @param states - training data for PCA
	 * @param numComponents - number of components for PCA
	 * @param clusterAmount - amount of clusters for k-means clustering
	 * @param iterations - amount of iterations for k-means clustering
	 */
	public PCAMeans(List<State> states, int numComponents, int clusterAmount, int iterations)
	{
		this(statesToVectors(states), numComponents, clusterAmount, iterations);
	}

	/**
	 * Create new PCAMeans by clustering the eigen space projections of 'vectors'
	 * @param vectors - training data for PCA
	 * @param numComponents - number of components for PCA
	 * @param clusterAmount - amount of clusters for k-means clustering
	 * @param iterations - amount of iterations for k-means clustering
	 */
	public PCAMeans(double[][] vectors, int numComponents, int clusterAmount, int iterations)
	{
		this.numComponents = numComponents;
		this.clusterAmount = clusterAmount;
		this.iterations = iterations;
		this.projectToMeanCache = new HashMap<Instance, Integer>();
		// perform PCA
		int numSamples = vectors.length;
		int sampleSize = vectors[0].length;
		this.pca = new PrincipleComponentAnalysis(numSamples, sampleSize);
		pca.addSamples(vectors);
		pca.computeBasis(numComponents);
		double[][] projections = pca.samplesToEigenSpace(vectors);
		
		// cluster PCA results	
		if( clusterAmount < 0 )	
		{
			this.means = doubleArrayToDataset(projections); // each projection is a cluster
			for(int i=0; i<means.size(); i++)
			{
				projectToMeanCache.put(means.get(i), i); // cache the conversions
			}
		}else
		{
			Dataset[] clusters = createClusters(projections, clusterAmount, iterations);
			this.means = calculateMeans(clusters);
		}
		
		if( verbose==1 )
			datasetToMatFile(means);
	}//end constructors

	/**
	 * Convert a list of states to an array of vectors. Each vector is the 
	 * representation of the corresponding state
	 * @param states - list of states
	 * @return double array containing vectors
	 */
	private static double[][] statesToVectors(List<State> states)
	{
		// create vectors for PCA
		int numSamples = states.size();
		int sampleSize = states.get(0).getRepresentation().length;
		double[][] vectors = new double[numSamples][sampleSize];
		for(int i=0; i<vectors.length; i++)
			vectors[i] = states.get(i).getRepresentation();

		return vectors;
	}

	/**
	 * Create clusters of vectors.
	 * @param vectors - n vectors with m dimensions
	 * @param clusterAmount - amount of clusters for kMeans clustering
	 * @param iterations - amount of iterations for kMeans clustering
	 * @return Dataset[] containing clusters of vectors
	 */
	private Dataset[] createClusters(double[][] vectors, int clusterAmount, int iterations)
	{
		Clusterer km = new KMeans(clusterAmount, iterations);
		Dataset data = doubleArrayToDataset(vectors);
		return km.cluster(data);
	}

	/**
	 * Convert double array of vectors to a Dataset
	 * @param vectors - double array of vectors
	 * @return Dataset containing vectors
	 */
	private static Dataset doubleArrayToDataset(double[][] vectors)
	{
		Dataset data = new DefaultDataset();
		for (int i = 0; i < vectors.length; i++) 
		{
			Instance instance = new DenseInstance(vectors[i]);
			data.add(instance);
		}
		return data;
	}

	/**
	 * Calculate the means of clusters
	 * @param clusters - clusters of vectors
	 * @return Dataset containing means of each clusters
	 */
	private Dataset calculateMeans(Dataset[] clusters)
	{
		Dataset means = new DefaultDataset();
		int index = 0;
		for(Dataset dataset: clusters)
		{
			Instance sum = new DenseInstance(new double[dataset.get(0).noAttributes()]);
			for(Instance instance: dataset)
			{
				projectToMeanCache.put(instance, index);	// cache conversion
				sum = sum.add(instance);
			}
			means.add(sum.divide(dataset.size()));
			index++;
		}
		return means;
	}

	/**
	 * Write 'clusters' to file
	 * @param clusters - clusters of vectors
	 */
	public void datasetToMatFile(Dataset data)
	{ //TODO put in folder 'mat'
		String fileName = String.format("means_nC%d_cA%d_i%d.mat", 
				numComponents, clusterAmount, iterations);
		datasetToMatFile(data, fileName);
	}//end clustersToMatFile
	
	public static void datasetToMatFile(Dataset data, String fileName)
	{
		ArrayList<MLArray> matClusters = new ArrayList<MLArray>();
		double[][] meansDoubleArray = datasetToDoubleArray(data);
		MLArray matArray = new MLDouble("means", meansDoubleArray);
		matClusters.add(matArray);
		
		try {			
			System.out.printf("Write to file %s\n", fileName);
			new MatFileWriter( fileName, matClusters );	
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static double[][] datasetToDoubleArray(Dataset cluster)
	{
		double[][] doubleArray = new double[cluster.size()][];
		for(int i=0; i<cluster.size(); i++)
		{
			Instance vector = cluster.get(i);
			doubleArray[i] = instanceToArray(vector);
		}
		return doubleArray;
	}
	
	public static double[] instanceToArray(Instance vector)
	{
		double[] array = new double[vector.noAttributes()];
		for(int i=0; i<array.length; i++)
			array[i] = vector.value(i);
		return array;
	}

	/**
	 * Convert a vector to a mean-index, i.e. project the vector with PCA and find 
	 * the mean of the cluster that has the smallest distance to the projected vector.  
	 * @param sample	vector
	 * @return index of mean that belongs to the cluster that has the smallest distance
	 * to the projected sample.
	 */
	public int sampleToMean(double[] sample)
	{
		Instance projection = sampleToProjection(sample);
		if( projectToMeanCache.containsKey(projection) )
		{
			return projectToMeanCache.get(projection);
		}
		DistanceMeasure dm = new EuclideanDistance();
		Set<Instance> closestSet = means.kNearest(1, projection, dm);	// find nearest neighbor
		Iterator<Instance> iter = closestSet.iterator();
		Instance closest = iter.next();
		int nearestMeanIndex = means.indexOf(closest);	// find index of nearest neighbor
		projectToMeanCache.put(projection, nearestMeanIndex);	// cache conversion
		return nearestMeanIndex;
	}
	
	public Instance sampleToMeanProject(double[] sample)
	{
		Instance projection = sampleToProjection(sample);
		if( projectToMeanCache.containsKey(projection) )
		{
			int index = projectToMeanCache.get(projection);
			return means.get(index);
		}
		DistanceMeasure dm = new EuclideanDistance();
		Set<Instance> closestSet = means.kNearest(1, projection, dm);	// find nearest neighbor
		Iterator<Instance> iter = closestSet.iterator();
		Instance closest = iter.next();
		int nearestMeanIndex = means.indexOf(closest);	// find index of nearest neighbor
		projectToMeanCache.put(projection, nearestMeanIndex);	// cache conversion
		
		return closest;
	}
	
	/**
	 * Project a vector with PCA.
	 * @param sample	a vector
	 * @return	projection of given sample
	 */
	public Instance sampleToProjection(double[] sample)
	{
		return new DenseInstance(pca.sampleToEigenSpace(sample));
	}

}//end class
