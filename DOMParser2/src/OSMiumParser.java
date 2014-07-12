import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.*;
import org.openstreetmap.osmosis.core.task.v0_6.*;
import org.openstreetmap.osmosis.xml.common.CompressionMethod;
import org.openstreetmap.osmosis.xml.v0_6.XmlReader;
import org.postgis.Point;

import com.sun.xml.internal.bind.v2.runtime.reflect.Accessor.GetterSetterReflection;


public class OSMiumParser {
	
	HashMap<Long, Node> nodesMap = new HashMap<Long, Node>();
	HashMap<Long,List<Long>> nodesToWay = new HashMap<Long,List<Long>>();
	HashMap<Long,List<Long>> nodesToRelation = new HashMap<>();
	HashMap<Long,List<Long>> waystoRelation = new HashMap<>();
	HashMap<Long, Node> tempNodes = new HashMap<Long, Node>();
	HashMap<Long, Way> waysMap = new HashMap<Long, Way>();
	HashMap<Long, Relation> relationsMap = new HashMap<Long, Relation>();
	
	
	public OSMiumParser () {
		 // the input file
		File file = new File("C:/Users/Maurice/Drive/UNI/BachelorArbeit/Datenbasis/darmstadt2.osm");
		Sink sinkImplementation = new Sink() {
		    public void process(EntityContainer entityContainer) {
		        Entity entity = entityContainer.getEntity();
		        if (entity instanceof Node) {
		            Node current = (Node) entity;
		            tempNodes.put(current.getId(), current);
		        } else if (entity instanceof Way) {
		        	Way current = (Way) entity;
		        	if(isStreet(current)) {
		        		
		        		waysMap.put(current.getId(), current);
		        		addNodesFromWays(current);
		        	}
		        } else if (entity instanceof Relation) {
		            Relation current = (Relation) entity;
		            relationsMap.put(current.getId(), current);
		            addNodesFromRelation(current);
		        }
		    }
		    public void release() { }
		    public void complete() { }
			@Override
			public void initialize(Map<String, Object> arg0) {
				// TODO Auto-generated method stub
				
			}
		};
	
		boolean pbf = false;
		CompressionMethod compression = CompressionMethod.None;
	
		if (file.getName().endsWith(".pbf")) {
		    pbf = true;
		} else if (file.getName().endsWith(".gz")) {
		    compression = CompressionMethod.GZip;
		} else if (file.getName().endsWith(".bz2")) {
		    compression = CompressionMethod.BZip2;
		}
	
		RunnableSource reader = null;
	
		if (pbf) {
		    try {
				reader = new crosby.binary.osmosis.OsmosisReader(
				        new FileInputStream(file));
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
		    reader = new XmlReader(file, false, compression);
		}
	
		reader.setSink(sinkImplementation);
	
		Thread readerThread = new Thread(reader);
		readerThread.start();
	
		while (readerThread.isAlive()) {
		    try {
		        readerThread.join();
		    } catch (InterruptedException e) {
		        /* do nothing */
		    }
		}
	}
	protected void addNodesFromRelation(Relation current) {
		List<RelationMember> members = current.getMembers();
		for (RelationMember relationMember : members) {
			switch (relationMember.getMemberType()) {
			case Way:
				if(waystoRelation.containsKey(relationMember.getMemberId())){
					waystoRelation.get(relationMember.getMemberId()).add(current.getId());
				}
				else{
					List<Long> memberList = new ArrayList<>();
					memberList.add(current.getId());
					waystoRelation.put(relationMember.getMemberId(), memberList);
				}
				break;
			case Node:
				if(nodesToRelation.containsKey(relationMember.getMemberId())){
					nodesToRelation.get(relationMember.getMemberId()).add(current.getId());
				}
				else{
					List<Long> memberList = new ArrayList<>();
					memberList.add(current.getId());
					nodesToRelation.put(relationMember.getMemberId(), memberList);
				}
				break;
			default:
				break;
			}
		}
		
	}
	private int addNodesFromWays(Way current) {
		List<WayNode> wayNodes = current.getWayNodes();
		for (WayNode wayNode : wayNodes) {
			nodesMap.put(wayNode.getNodeId(), tempNodes.get(wayNode.getNodeId()));
			if(nodesToWay.containsKey(wayNode.getNodeId())){
				nodesToWay.get(wayNode.getNodeId()).add(current.getId());
			}
			else {
				List<Long> wayList = new ArrayList<>();
				wayList.add(current.getId());
				nodesToWay.put(wayNode.getNodeId(), wayList);
			}
		}
		return wayNodes.size();
	}
	public HashMap<Long, Node> getNodes() {
		return nodesMap;
	}
	public HashMap<Long, Way> getWays() {
		return waysMap;
	}
	public HashMap<Long, Relation> getRelations() {
		return relationsMap;
	}
	
	private boolean isStreet(Way way) {
		
		Collection<Tag> tags = way.getTags();
		boolean isStreet = false;
		for (Tag tag : tags) {
			if(tag.getKey().equals("highway") && !tag.getValue().equals("pedestrian") && !tag.getValue().equals("footway")&&!tag.getValue().equals("steps") &&!tag.getValue().equals("service") &&!tag.getValue().equals("path") && !tag.getValue().equals("cycleway") && !tag.getValue().equals("track") && !tag.getValue().equals("platform"))
				isStreet=true;
			else if(tag.getKey().equals("railway"))
				isStreet=false;
		}
		return isStreet;
	}
	public List<Way> getWays(Node node) {
		if(!nodesToWay.containsKey(node.getId()))
			System.out.println("Problem");
		List<Long> wayIDs = nodesToWay.get(node.getId());
		List<Way> ways = new ArrayList<>();
		for (Long id : wayIDs) {
			ways.add(waysMap.get(id));
		}
		return ways;
	}
	/**
	 * Returns the Nodes from which one can enter the street, in case of oneWays its the first Node, in case of non-oneways both endpoints are considered
	 * @param startnode 
	 * @return a list of all startnodes
	 */
	public List<Way> isStartNodeOf(Node startnode, Way ignoreWay){
		List<Long> wayIDs = nodesToWay.get(startnode.getId());
		List<Way> ways = new ArrayList<Way>();
		List<WayNode> wayNodes;
		for (Long index : wayIDs) {
			Way way = waysMap.get(index);
			wayNodes = way.getWayNodes();
			if(startnode.getId() == wayNodes.get(0).getNodeId() ||(!isOneWay(way) && startnode.getId() == wayNodes.get(wayNodes.size()-1).getNodeId())&& (ignoreWay==null || index != ignoreWay.getId())){
				ways.add(way);
			}	
		}
		return ways;
	}
	
	public List<Way> isEndNodeOf(Node endnode) {
		List<Long> wayIDs = nodesToWay.get(endnode.getId());
		List<Way> ways = new ArrayList<Way>();
		List<WayNode> wayNodes;
		for (Long index : wayIDs) {
			Way way = waysMap.get(index);
			wayNodes = way.getWayNodes();
			if(endnode.getId() == wayNodes.get(0).getNodeId() || endnode.getId()==wayNodes.get(wayNodes.size()-1).getNodeId())
				ways.add(way);
		}
		return ways;
	}
	
	public boolean isOneWay(Way way) {
		Collection<Tag> tags = way.getTags();
		for (Tag tag : tags) {
			if(tag.getKey().equals("oneway") && tag.getValue().equals("yes"))
				return true;
		}
		return false;
	}
	/**
	 * Calcultes the straightest Way to follow in the given direction
	 * @param startway 
	 * @param direction true if lastnode is lastnode, false if lastnode is startnode)
	 * @return
	 */
	public Way followWay(Way startway, boolean direction) {
		if(startway == null) {
			System.out.println("Nothing to follow");
			return null;
		}
		List<WayNode> startwayNodes = startway.getWayNodes();
		
		Node lastNode = direction ? nodesMap.get(startwayNodes.get(startwayNodes.size()-1).getNodeId()) : nodesMap.get(startwayNodes.get(0).getNodeId());
		List<Way> nextWays = isStartNodeOf(lastNode, null);
		double straightness;
		double maxStraightness =0;
		Way straightestWay = null;
		for (Way way : nextWays) {
			if(way.equals(startway))
				continue;
			straightness=isStraight(startway, way);
			//System.out.println("Checked "+ startway.getId() + " and "+way.getId()+ " with "+straightness);
			if(straightness>maxStraightness) {
				maxStraightness=straightness;
				straightestWay=way;
			}
		}
		
		return straightestWay;
	}
	
	public List<Way> getFollower(Way way, int count) {
		List<Way> followWays = new ArrayList<>();
		Way oldWay = way;
		Way newWay;
		boolean direction = true; //TODO: anfangsrichtung ist nicht immer "geradeaus" 
		for (int i = 0; i <count; i++) {
			newWay = followWay(oldWay, direction);
			if(newWay != null && !followWays.contains(newWay)) {
				followWays.add(newWay);
			}
			else {
				break;
			}
			direction= getDirection(oldWay, newWay);
			oldWay=newWay;
		}
		return followWays;
	}
	
	public double isStraight(Way currentWay, Way nextWay) {
		Node p1 = getFirstNode(currentWay);
		Node p2 = getLastNode(currentWay);
		Node p3 = getFirstNode(nextWay);
		Node p4 = getLastNode(nextWay);
		
		if(p1.equals(p3))
			return getAngle(p2, p1, p4);
		else if(p1.equals(p4))
			return getAngle(p2, p1, p3);
		else if (p2.equals(p3))
			return getAngle(p1, p2, p4);
		else if (p2.equals(p4))
			return getAngle(p1, p2, p3);
		else {
			System.out.println("Strange Case - isStraight");
			return -1;
		}
			
	}
	
	public Point pointToNode(Node node) {
		return new Point(node.getLatitude(), node.getLongitude());
	}
	public double getAngle(Node n1, Node n2, Node n3) {
		return getAngle(pointToNode(n1), pointToNode(n2), pointToNode(n3));
	}
	
	public double getAngle(Point p1, Point p2, Point p3) {
		double p1p2 = p1.distance(p2);
		double p1p3 = p1.distance(p3);
		double p2p3 = p2.distance(p3);
		return Math.toDegrees(Math.acos((Math.pow(p1p2, 2.0)+Math.pow(p2p3, 2.0)-Math.pow(p1p3, 2.0))/(2.0*p1p2*p2p3))); 
	}
	
	public Node getNodeFromWayNode(WayNode waynode){
		return nodesMap.get(waynode.getNodeId());
	}
	
	public Node getLastNode(Way way) {
		List<WayNode> wayNodes = way.getWayNodes();
		return getNodeFromWayNode(wayNodes.get(wayNodes.size()-1));
	}
	public Node getSecondLastNode(Way way) {
		List<WayNode> wayNodes = way.getWayNodes();
		return getNodeFromWayNode(wayNodes.get(wayNodes.size()-2));
	}
	public Node getFirstNode(Way way) {
		List<WayNode> wayNodes = way.getWayNodes();
		if(wayNodes.isEmpty())
			return null;
		return getNodeFromWayNode(wayNodes.get(0));
	}
	
	public Node getNode(Long id) {
		return nodesMap.get(id);
	}
	
	public Way getWay(Long id) {
		return waysMap.get(id);
	}
	
	public boolean getDirection(Way currentWay, Way nextWay) {
		Node p1 = getFirstNode(currentWay);
		Node p2 = getLastNode(currentWay);
		Node p3 = getFirstNode(nextWay);
		Node p4 = getLastNode(nextWay);
		
		return (p1.equals(p3) || p2.equals(p3));

	}
	
	public double getDistanceBetween(Node n1, Node n2) {
		Point p1 = pointToNode(n1);
		Point p2 = pointToNode(n2);
		return p1.distance(p2);
	}

	
}
