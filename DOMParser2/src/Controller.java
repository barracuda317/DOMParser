import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Vector;

import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.RelationMember;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;
import org.postgis.Point;

import com.sun.corba.se.spi.legacy.connection.GetEndPointInfoAgainException;

import org.jscience.mathematics.vector.Float64Vector;

public class Controller {
	OSMiumParser parser;
	DB db;
	List<Crossroad> crossroads;
	HashMap<Way, List<Crossroad>> inputWayToCrossroad;
	HashMap<Way, List<Crossroad>> outputWayToCrossroad;
	
	public static void main(String argv[]) {
		OSMiumParser parser = new OSMiumParser();
		DB db = new DB(parser);
		Controller cont = new Controller(parser,db);
		Crossroad currentCrossroad;
		List<Crossroad> crossroadList = new ArrayList<>();
		
		for (int i = 1; i < 150 ; i++) {
			if(!db.isValid(i)) {
				//System.out.println(i + " is not valid" );
				continue;
			}
			currentCrossroad = cont.importCrossroad(i,1);
			if(currentCrossroad!=null) {
				System.out.println("Importing Crossroad " + db.getCrossroadName(i));
				crossroadList.add(currentCrossroad);
				//db.writeCrossroad(currentCrossroad);
				cont.addInputWays(currentCrossroad, currentCrossroad.getInputWays());
				cont.addOutputWays(currentCrossroad, currentCrossroad.getOutputWays());
			}
		}
		System.out.println("#InputWays " + cont.inputWayToCrossroad.size());
		System.out.println("#OutputWays " + cont.outputWayToCrossroad.size()+ "\n");
		System.out.println("CONNECTING");
		cont.doit2();
		/*
		currentCrossroad= cont.getCrossroad(94);
		currentCrossroad.process();
		currentCrossroad.toFile();
		cont.addCrossroad(currentCrossroad, crossroadList);
		*/
		//cont.doit();
		//cont.connectAllOutputWays();
	}
	public void doit2() {
		List<Crossroad> crossroads;
		int i=0;
		int j=0;
 		for (Way outWay : outputWayToCrossroad.keySet()) {
			crossroads = outputWayToCrossroad.get(outWay);
			if(outWay.getId()==150955022)
				System.out.println("#Crossroads "+crossroads.size());
			for (Crossroad crossroad : crossroads) {
				if(isConnectedToNewCrossroad(outWay, crossroad))
					System.out.println("Nice!!");
				else
					findNextCrossroad(outWay, crossroad);
			}
			
		}
	}
	public void doit() {
		Way tempWay;
		Way oldWay;
		boolean direction =true;
		for (Way way : outputWayToCrossroad.keySet()) {
			if(way==null)
				continue;
			direction =true;
			oldWay=way;
			System.out.println(oldWay.getId());
			for (int i = 0; i < 10; i++) {
				tempWay = parser.followWay(oldWay,direction);
				if(tempWay == null) 
					break;
				System.out.println(tempWay.getId());
				direction = parser.getDirection(oldWay,tempWay);
				oldWay = tempWay;
				if(inputWayToCrossroad.containsKey(tempWay))
					System.out.println("Connection "+way.getId()+" on "+db.getCrossroadName(outputWayToCrossroad.get(way).get(0).id)+"  with " + db.getCrossroadName(inputWayToCrossroad.get(tempWay).get(0).id) +" on road " + tempWay.getId());
				
			}
			System.out.println("---------------");
		}
	}
	public void connectAllOutputWays() {
		for (Way outputWay : outputWayToCrossroad.keySet()) {
			connectWayToCrossroad(outputWay);
		}
	}
	public void addCrossroad(Crossroad crossroad , List<Crossroad> crossroadList) {
		crossroadList.add(crossroad);
		addInputWays(crossroad, crossroad.getInputWays());
		addOutputWays(crossroad, crossroad.getOutputWays());
	}
	public void followWay(Way way) {
		Way newWay;
		Way oldWay = way;
		boolean direction = true;
		for (int i = 0; i < 10; i++) {
			newWay = parser.followWay(oldWay,direction);
			direction = parser.getDirection(oldWay, newWay);
			
			System.out.println("Next Way " + newWay.getId() + " found with direction: " + direction);
			oldWay= newWay;
		}
	}
	
	public int addInputWays(Crossroad crossroad, List<Way> inputWays) {
		int count =0;
		List<Crossroad> tempList;
		for (Way way : inputWays) {
			if(way==null || crossroad == null)
				continue;
			if(inputWayToCrossroad.containsKey(way)) {
				inputWayToCrossroad.get(way).add(crossroad);
			}
			else {
				tempList = new ArrayList<>();
				tempList.add(crossroad);
				inputWayToCrossroad.put(way, tempList);
			}
			count++;
		}
		return count;
	}
	
	
	public int addOutputWays(Crossroad crossroad, List<Way> outputWays) {
		int count =0;
		List<Crossroad> tempList;
		for (Way way : outputWays) {
			if(way==null || crossroad == null)
				continue;
			if(outputWayToCrossroad.containsKey(way)){
				outputWayToCrossroad.get(way).add(crossroad);
			}
			else {
				tempList = new ArrayList<>();
				tempList.add(crossroad);
				outputWayToCrossroad.put(way, tempList);
			}
			count++;
		}
		return count;
	}
	public Controller(OSMiumParser parser, DB db) {
		this.parser=parser;
		this.db=db;
		inputWayToCrossroad = new HashMap<>();
		outputWayToCrossroad = new HashMap<>();
	}
	
	public Crossroad getCrossroad(int crossroadID) {
		List<Crossroad> crossroads = getCrossroads(crossroadID,crossroadID);
		if(crossroads.size()>0)
			return crossroads.get(0);
		else
			return null;
	}
	
	private List<Crossroad> getCrossroads() {
		return getCrossroads(0, 150);
	}
	
	
	
	private List<Crossroad> getCrossroads(int from, int to) {
		List<Crossroad> crossroads = new ArrayList<>();
		for (int i = from; i <= to; i++) {
			if(i==3){
				System.out.println("Crossroad "+db.getCrossroadName(i) + " with ID "+i+" can not be processed");
				continue;
			}	
			crossroads.add(new Crossroad(db.getSensors(i),i,parser,db));
		}
		this.crossroads=crossroads;
		return crossroads;
	}
	
	public Crossroad importCrossroad(int crossroadID, int type) {
		return (type==0) ? importCrossroadFromFile(crossroadID) : db.importCrossroad(crossroadID);
	}
	
	public Crossroad importCrossroadFromFile(int crossroadID) {
		//System.out.println("Importing "+crossroadID);
		String fileName = db.getCrossroadName(crossroadID)+".txt";
		int mode = 0;
		int id = 0;
		List<Way> inputWays = new ArrayList<>();
		List<Way> outputWays = new ArrayList<>();
		try {
			BufferedReader in = new BufferedReader(new FileReader(fileName));
			String zeile = in.readLine();
			if(zeile == null)
				System.out.println(crossroadID);
			if(!zeile.equals("MODIFIED") && !zeile.equals("NOTHING")) {
				System.out.println("Crossroad "+ db.getCrossroadName(crossroadID) +" has not been modified, be careful");
				return null;
			}
			if(!db.isValid(crossroadID)) {
				System.out.println(crossroadID + " is invalid");
				return null;
			}
			while ((zeile = in.readLine()) != null) {
				if(zeile.equals("ID"))
					mode = 1;
				else if(zeile.equals("InputWays:"))
					mode = 2;
				else if(zeile.equals("OutputWays:"))
					mode = 3;
				else if(zeile.equals("##########"))
					break;
				else if(zeile.length()==0)
					continue;
				else {
					switch (mode) {
					case 1:
						id=Integer.valueOf(zeile);
						break;
					case 2:
						inputWays.add(parser.getWay(new Long(zeile)));
						break;
					case 3:
						outputWays.add(parser.getWay(new Long(zeile)));
						break;
					default:
						break;
					}
				}
			}
		} catch (FileNotFoundException e) {
			System.out.println("Crossroad with ID "+crossroadID+" doesn't exists");
		}  catch (Exception e) {
			e.printStackTrace();
		}
		return new Crossroad(db.getSensors(crossroadID), id, inputWays,outputWays, parser, db);
	}
	
	public boolean findNextCrossroad(Way outputWay, Crossroad crossroad) {
		System.out.println("Search connenect Crossroad for OutputWay " + outputWay.getId() +  " on crossroad " + db.getCrossroadName(crossroad.id) +" ("+crossroad.id+") ");
		// Pfad der Länge 1
		boolean connected = false;
		if(isConnectedToNewCrossroad(outputWay, crossroad)) {
			System.out.println("-----------------------------------------------------------");
			return true;
		}
		else {
			Way oldWay = outputWay;
			Way newWay;
			boolean direction = crossroad.getDirectionOutput(outputWay);
			for (int i = 0; i < 10; i++) {
				newWay =parser.followWay(oldWay, direction);
				if(newWay==null) {
					System.out.println("No Connection found");
					System.out.println("-----------------------------------------------------------");
					return connected;
				}
				else
					System.out.println("Next Way "+ newWay.getId());
				direction = parser.getDirection(oldWay, newWay);
				if(outputWayToCrossroad.keySet().contains(newWay)) {
					List<Crossroad> connectedCrossroads = outputWayToCrossroad.get(newWay);
					for (Crossroad connectedCrossroad : connectedCrossroads) {
						if(!db.connectionDenied(crossroad, connectedCrossroad) || !db.isAlreadyConnected(crossroad, connectedCrossroad))
							System.out.println("Found Connection with: " + newWay.getId() + " on Crossroad " + db.getCrossroadName(connectedCrossroad.id) + " ("+connectedCrossroad.id+") ");
					}
					connected= true;
					break;
				}
				oldWay = newWay;
			}
			
		}
		if(!connected)
			System.out.println("No Connection found");
		System.out.println("-----------------------------------------------------------");
		return connected;
	}
	
	public boolean isConnectedToNewCrossroad(Way way, Crossroad crossroad) {
		boolean isConnected = false;
		List<Crossroad> nextCrossroads;
		if(inputWayToCrossroad.containsKey(way)) {
			nextCrossroads = inputWayToCrossroad.get(way);
			for (Crossroad nextCrossroad : nextCrossroads) {
				if(!nextCrossroad.equals(crossroad) && !db.isAlreadyConnected(crossroad,nextCrossroad) &&!db.connectionDenied(crossroad,nextCrossroad)){
					System.out.println("Connected  " + way.getId() + " on "+db.getCrossroadName(crossroad.id) +"("+crossroad.id+")"+" with " + way.getId() + " on " + db.getCrossroadName(nextCrossroad.id)+"("+nextCrossroad.id+")");
					db.insertConnection(crossroad, way, nextCrossroad, way);
					isConnected=true;
				}

			}
		}
		return isConnected;
	}
	public boolean connectWayToCrossroad(Way way){
		if(way==null)
			return false;
		System.out.println("Search connected Crossroad for Way " +way.getId());
		Way newWay;
		Way oldWay =way;
		boolean direction =true;
		System.out.println(oldWay.getId());
		for (int i = 0; i < 10; i++) {
			newWay = parser.followWay(oldWay,direction);
			if(newWay == null) 
				break;
			System.out.println(newWay.getId());
			direction = parser.getDirection(oldWay,newWay);
			oldWay = newWay;
			if(inputWayToCrossroad.containsKey(newWay) && !inputWayToCrossroad.get(newWay).equals(outputWayToCrossroad.get(way))) {
				return true;
			}
		}
		System.out.println("---------------");
		return true;
	}
}
