package code;
import java.io.*;
import java.util.*;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import format.StringIntegerList;
import format.StringIntegerList.StringInteger;

import java.net.URI;
/**
 * @author Hadoop Group 15
 * This class is to train all the data and get all the feature frequencies for each profession
 */
public class TrainningMapred {

	/**
	 * TrainningMapper class is to map all the professions to corresponding article 
	 * lemmas and their occurrences 
	 */
	public static class TrainningMapper extends Mapper<LongWritable, Text, Text, StringIntegerList> {
		//store people name and professions
		public static Map<String, LinkedList<String>> peopleProfessions = new HashMap<String, LinkedList<String>>();

		/**
		 * get the cached file people_train.txt and store the data into a hash map.
		 */
		protected void setup(
				Mapper<LongWritable, Text, Text, StringIntegerList>.Context context) throws IOException, InterruptedException {
			super.setup(context);
//			URI[] files = context.getCacheFiles();
			Path[] files = context.getLocalCacheFiles(); // get the cached path
			BufferedReader reader = new BufferedReader(new FileReader(new File(
					files[0].toString())));
			String currentLine = null;
			//read each line of the file
			while ((currentLine = reader.readLine()) != null) {
				//split the name and professions
				String[] splits = currentLine.split(":");
				String name = splits[0].trim();
				//add people name and create a linked list for professions
				peopleProfessions.put(name, new LinkedList<String>());
				String[] professions = splits[splits.length-1].trim().split(",");
				for (String profession: professions) {
					peopleProfessions.get(name).add(profession.trim());
				}
			}
			reader.close();
		}

		/**
		 * map the professions with article lemmas and occurrences
		 */
		public void map(LongWritable key, Text values, Context context) throws IOException, InterruptedException {
			String[] splits = values.toString().split("\t");
			if (splits.length > 1){
				String peopleName = splits[0].trim();
				String indices = splits[1].trim();
				//if the people name can be found in the people professions hash map
				if (peopleProfessions.containsKey(peopleName)){
					//split all indices to each lemmaIndex
					String[] lemmaIndices = indices.split(">,<");
					//iterate through each profession
					for (String profession: peopleProfessions.get(peopleName)) {
						Map<String, Integer> lemmas = new HashMap<String, Integer>();
						//iterate through each lemma
						
						for (int i= 0; i<lemmaIndices.length; i++) {	
							String tuple = lemmaIndices[i].replaceAll("[<>]", "");
							String[] parts = tuple.split(",");
							String lemma = parts[0];
							int freq = Integer.parseInt(parts[1]);
							lemmas.put(lemma, freq);		
						}
						StringIntegerList list = new StringIntegerList(lemmas);
						context.write(new Text(profession), list);
					}
				}
			}
		}
	}

	/**
	 * TrainningReducer class is to count all the total occurrences of each lemma in each profession
	 */
	public static class TrainningReducer extends Reducer<Text, StringIntegerList, Text, StringIntegerList> {
		public void reduce(Text profession, Iterable<StringIntegerList> lemmaLists, Context context) throws IOException, InterruptedException {
			int articleNumber = 0;
			//store all the lemmas and their frequency in each profession
			Map<String, Integer> lemmaFreq = new HashMap<String, Integer>();
			//iterate through each list
			for (StringIntegerList list: lemmaLists) {
				articleNumber ++;
				//iterator through each <lemma, 1>
				for (StringInteger unit: list.getIndices()) {
					String lemma = unit.getString();
					int value;
					if (!lemmaFreq.containsKey(lemma))
						value = 1;
					else
						value = lemmaFreq.get(lemma) + 1;
					lemmaFreq.put(lemma, value);
				}
			}
			StringIntegerList result = new StringIntegerList(lemmaFreq);
			String key = profession + "\ttotal articles: " + String.valueOf(articleNumber);
			//output "profession (total_article_number) /t <feature1, freq1> <feature2, freq2> ...
			context.write(new Text(key), result);
		}
	}

	/**
	 * run TranningMapred class
	 */
	public static void main(String[] args) throws Exception {
		Job job = Job.getInstance();
		job.setJarByClass(TrainningMapred.class);
		job.setJobName("Trainning");
		job.getConfiguration().set("mapreduce.job.queuename","hadoop15");

		job.setMapperClass(TrainningMapper.class);
		job.setReducerClass(TrainningReducer.class);

		job.setMapOutputValueClass(StringIntegerList.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(StringIntegerList.class);

		job.setInputFormatClass(TextInputFormat.class);
		job.setOutputFormatClass(TextOutputFormat.class);

	//	FileInputFormat.setInputPaths(job, new Path("hdfs://localhost:9000/input"));
	//	FileOutputFormat.setOutputPath(job, new Path("hdfs://localhost:9000/output"));
		
		FileInputFormat.setInputPaths(job, new Path(args[0]));
		FileOutputFormat.setOutputPath(job, new Path(args[1]));

		//job.addCacheFile(new URI("/Users/Yahui/Desktop/profession_train.txt"));
		job.addCacheFile(new URI(args[2]));

		job.waitForCompletion(true);
	}
}
