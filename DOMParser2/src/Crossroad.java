import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import javax.print.attribute.standard.MediaSize.Other;

import org.javatuples.Pair;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;
import org.postgis.Point;


public class Crossroad {
	List<Sensor> sensors;
	List<Way> inputWays;
	List<Way> outputWays;
	HashMap<Way,Pair<Crossroad, Way>> outputConnectors;
	HashMap<Way,Pair<Crossroad, Way>> inputConnectors;
	OSMiumParser parser;
	DB db;
	int id;
	
	public Crossroad(List<Sensor> sensors,int id, OSMiumParser parser, DB db) {
		this.sensors = sensors;
		this.parser=parser;
		this.db = db;
		inputWays = new ArrayList<>();
		outputWays = new ArrayList<>();
		this.id = id;
	}
	
	public Crossroad(List<Sensor> sensors, int id, List<Way> inputWays,
			List<Way> outputWays, OSMiumParser parser, DB db) {
		this.sensors= sensors;
		this.parser=parser;
		this.db=db;
		this.inputWays=inputWays;
		this.outputWays=outputWays;
		this.id=id;
	}

	public void process() {
		System.out.println("Processing " + db.getCrossroadName(id) +" with ID "+id+" ... ");
		Way currentWay;
		for (Sensor sensor : sensors) {
			sensor.nearestNode();
			//System.out.println("Sensor mit SensorID: " + sensor.id+" on Node : " +sensor.nearestNode.getId());
			//System.out.println(" with Way "+ sensor.getCurrentWay().getId());
			currentWay=sensor.getCurrentWay();
			if(!inputWays.contains(currentWay)) {
				inputWays.add(currentWay);
			}
			
		}
		//cleanInputWays();
		calculateOutputWays();
		cleanOutputWays();
		
	}
	/**
	 * removes all inputWays which are dispensable and adds new ones, which merge several inputways
	 */
	private void cleanInputWays() {
		Node node1;
		Node node2;
		Node firstNode;
		Node lastNode;
		List<Way> nextWays;
		List<Way> previousWays = new ArrayList<>();
		List<Way> removeInputWay = new ArrayList<>();
		List<Node> directionNodes;
		for (Way possibleInputWay : inputWays) {
			directionNodes = getStartLast(possibleInputWay);
			firstNode=directionNodes.get(0);
			lastNode=directionNodes.get(1);
			
			// Check if two inputWays A and B are connected with way C.
			previousWays.addAll(parser.isEndNodeOf(firstNode));
			while(previousWays.contains(possibleInputWay)) {
				previousWays.remove(possibleInputWay);
			}
		}
		for (Way previousWay : previousWays) {
			if(removeInputWay.indexOf(previousWay) != removeInputWay.lastIndexOf(previousWay) && !inputWays.contains(previousWay))
				inputWays.add(previousWay);
		}
		// Check if inputWay A leads to another inputWay B, if yes, remove B
		for (Way possibleInputWay : inputWays) {
			directionNodes = getStartLast(possibleInputWay);
			firstNode=directionNodes.get(0);
			lastNode=directionNodes.get(1);
			nextWays = parser.isEndNodeOf(lastNode);
			while(nextWays.contains(possibleInputWay)) {
				nextWays.remove(possibleInputWay);
			}
			for (Way connected : nextWays) {
				if(inputWays.contains(connected) && !removeInputWay.contains(connected) &&!isOppositeWay(connected, possibleInputWay)){
					removeInputWay.add(connected);
				}
			}
		}
		for (Way removeWay : removeInputWay) {
			inputWays.remove(removeWay);
		}
	}
	
	
	/**
	 * returns true, if way1 and way2 are heading in oppsotive direction
	 * @param way1
	 * @param way2
	 * @return
	 */
	private boolean isOppositeWay(Way way1, Way way2) {
		boolean direction =getDirectionInput(way1);
		Way oldWay=way1;
		boolean directed =false;
		Way tempWay;
		for (int i = 0; i < 4; i++) {
			tempWay = parser.followWay(oldWay,direction);
			if(tempWay == null) {
				return false;
			}
			if(tempWay.equals(way2)) {
				directed = true;
				break;
			}
			if(tempWay == null) 
				break;
			direction = parser.getDirection(oldWay,tempWay);
			oldWay = tempWay;
		}
		if(directed){
			oldWay = way2;
			direction = getDirectionInput(way2);
			for (int i = 0; i < 4; i++) {
				tempWay = parser.followWay(oldWay,direction);
				if(tempWay.equals(way1)) {
					return true;
				}
				if(tempWay == null) 
					break;
				direction = parser.getDirection(oldWay,tempWay);
				oldWay = tempWay;
			}
		}
		return false;
	}
	
	/**
	 * InputWays: returns true, if driving from start to last, and false if driving vom last to start 
	 * @param way
	 * @return
	 */
	public boolean getDirectionInput(Way way) {
		List<Node> directionNodes = getStartLast(way);
		if(directionNodes.get(0) == parser.getFirstNode(way))
			return true;
		else
			return false;
	}
	
	public boolean getDirectionOutput(Way way) {
		return !getDirectionInput(way);
	}
	/**
	 * return a list of Nodes, where element 0 is the firstnode and element 1 the lastnode 
	 * @param way
	 * @return
	 */
	public List<Node> getStartLast(Way way) {
		List<Node> nodeList = new ArrayList<>();
		Node node1 = parser.getFirstNode(way);
		Node node2 = parser.getLastNode(way);
		Point center = db.getCenter(id);
		if(center.distance(parser.pointToNode(node1))<center.distance(parser.pointToNode(node2))){
			nodeList.add(node2);
			nodeList.add(node1);
		}
		else {
			nodeList.add(node1);
			nodeList.add(node2);
		}
		return nodeList;
	}
	
	//TODO: Füge OutputWays zusammen und verhindere gegenseitige Auslöschung
	private void calculateOutputWays() {
		HashMap<Way,List<Way>> outputPaths;      
		List<Way> possibleOutputWays = new ArrayList<>();
		if(inputWays.size()>0) {
			for (Way way : inputWays) {
				if(way==null) {
					continue;
				}
				List<Way> ways = parser.isStartNodeOf(getStartLast(way).get(1),way);
				for (Way way2 : ways) {
					if(!possibleOutputWays.contains(way2))
						possibleOutputWays.add(way2);
				}
			}
			outputWays=possibleOutputWays;
		}
	}


	private void cleanOutputWays() {
		HashMap<Way,List<Way>> outputMap = new HashMap<>();
		int otherCount; // counts the matched way in the path for another Outputway;
		int otherIndex =0;
		int myIndex =0;
		Way myWay;
		boolean match =false;
		List<Way> follower;
		for (Way possibleOutputWay : outputWays) {
			follower = new ArrayList<>();
			follower.add(possibleOutputWay);
			follower.addAll(parser.getFollower(possibleOutputWay,5));
			outputMap.put(possibleOutputWay,follower );
		}
		int[] indexMatches = new int[outputMap.size()];
		for (int i : indexMatches) {
			i=0;
		}
		for (Entry<Way,List<Way>> entry : outputMap.entrySet()) {
			match=false;
			List<Way> myFollower = entry.getValue();
			List<Way> otherFollower;
			for (int i=indexMatches[myIndex]; i<myFollower.size()&&!match;i++) {
				myWay=myFollower.get(i);
				otherIndex=0;
				for (Entry<Way,List<Way>> otherEntry : outputMap.entrySet()) {
					if(otherEntry.equals(entry) || indexMatches[otherIndex]<0) {
						otherIndex++;
						continue;
					}
					else {
						otherFollower=otherEntry.getValue();
						otherCount=0;
						for (Way otherWay : otherFollower) {
							//System.out.println("Checking " +otherWay.getId() +" and " +myWay.getId());
							if(otherWay.equals(myWay)) {
								//System.out.println("Match with " +otherWay.getId());
								indexMatches[myIndex] = -1;
								if(otherCount> indexMatches[otherIndex])
									indexMatches[otherIndex] = otherCount;
								match=true;
								break;
							}
							otherCount++;
						}
					}
					otherIndex++;
				}
			}
			myIndex++;
		}
		myIndex=0;
		for (Entry<Way,List<Way>> entry : outputMap.entrySet()) {
			if(indexMatches[myIndex]<0) {
				outputWays.remove(entry.getKey());
				System.out.println("Removed " + entry.getKey().getId() + " because it leads to another path");
			}
			else if (indexMatches[myIndex] == 0) {
				System.out.println(entry.getKey().getId() + " does not lead to antoher path or is matched by antother path");
				myIndex++;
				continue;
			}
			else {
				System.out.println("Replaced " +entry.getKey().getId() + " by " + entry.getValue().get(indexMatches[myIndex]).getId());
				outputWays.remove(entry.getKey());
				outputWays.add(entry.getValue().get(indexMatches[myIndex]));
			}
			myIndex++;
		}
		
	}
	
	

	private void addCommonOutputWays() {
		Way tempWay;
		Way oldWay;
		List<Way> tempOutputWays = new ArrayList<>();
		List<Way> tempCommonOutputWays = new ArrayList<>();
		List<Way> removeOutputWays = new ArrayList<>();
		boolean direction =true;
		for (Way outputWay : outputWays) {
			oldWay = outputWay;
			for (int i = 0; i < 5; i++) {
				tempWay = parser.followWay(oldWay,direction);
				direction = true;
				if(!tempOutputWays.contains(tempWay)&& !outputWays.contains(tempWay)) {
					tempOutputWays.add(tempWay);
				}
				else if(tempOutputWays.contains(tempWay)){
					tempCommonOutputWays.add(tempWay);
					removeOutputWays.add(outputWay);
					break;
				}
				else if(outputWays.contains(tempWay))
					break;
				oldWay=tempWay;
			}
		}
		for (Way removeWay : removeOutputWays) {
			System.out.println("Removing .. " +removeWay.getId());
			outputWays.remove(removeWay);
		}
		for (Way commonOutputWay : tempCommonOutputWays) {
			if(!outputWays.contains(commonOutputWay))
				outputWays.add(commonOutputWay);
		}
		
	}

	private boolean leadsToOutputWay(Way way) {
		Way nextWay = way;
		for (int i = 0; i < 5; i++) {
			nextWay = parser.followWay(nextWay,true);
			if(outputWays.contains(nextWay)) {
				return true;
			}
		}
		return false;
	}

	public void toFile() {
		String lineSeparator = System.getProperty("line.separator");
		File file = new File(db.getCrossroadName(id)+".txt");
		if(isModified()) {
			return;
		}
		try {
			FileWriter writer = new FileWriter(file);
			writer.write("ID"+lineSeparator);
			writer.write(id+lineSeparator);
			writer.write("InputWays:" +lineSeparator);
			for (Way inputWay : inputWays) {
				if(inputWay == null)
					continue;
				writer.write(inputWay.getId() + lineSeparator);
			}
			writer.write("OutputWays:" +lineSeparator);
			for (Way outputWay : outputWays) {
				writer.write(outputWay.getId() + lineSeparator);
			}
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public boolean isModified() {
		String fileName = db.getCrossroadName(id)+".txt";
		File file = new File(db.getCrossroadName(id)+".txt");
		if(file.exists()) {
			try {
				BufferedReader in = new BufferedReader(new FileReader(fileName));
				String line = in.readLine();
				if(line != null && ( line.equals("MODIFIED") || line.equals("NOTHING") || line.equals("NOCOORD") || line.equals("OUTSIDE")) ) {
					System.out.println("Crossroad has already been modified");
					return true;
				}
			
			} catch (IOException e) {
				e.printStackTrace();
			}
			}
		return false;
	}

	public List<Way> getOutputWays() {
		return outputWays;
	}
	
	
	public List<Way> getInputWays() {
		return inputWays;
	}
	
}
