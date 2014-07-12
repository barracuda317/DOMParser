import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingQueue;

import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.RelationMember;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.postgis.Point;

import com.google.common.collect.MinMaxPriorityQueue;


public class Sensor {
	int id;
	double lat;
	double lon;
	String realname;
	int crossroadID;
	Node nearestNode;
	Way currentWay;
	Node outputNode;
	private OSMiumParser parser;
	private DB db;
	Queue nearestNodes;
	
	public Sensor (int id, double lat, double lon, String realname, int crossroadID,OSMiumParser parser, DB db) {
		this.id = id;
		this.lat = lat;
		this.lon = lon;
		this.realname=realname;
		this.parser =parser;
		this.db = db;
		this.crossroadID= crossroadID;
	}

	public Node nearestNode() {
		nearestNodes = MinMaxPriorityQueue.orderedBy(new NodesComparator(new Point(lat,lon))).maximumSize(3).create();
		HashMap<Long, Node> nodes = parser.getNodes();
		for (Node node : nodes.values()) {
			nearestNodes.add(node);
		}
		return nearestNode=(Node) nearestNodes.peek();
	}
	public String toString() {
		
		String output = "Sensor " +realname + " on "+db.getCrossroadName(this.crossroadID)+"  near Node " +this.nearestNode.getId() + " on Way " +currentWay.getId() + "\n";
		if(outputNode== null || nearestNode == null) {
			return output+"\n No further Infos"+"\n----------------------------------------------------------------------";
		}
		String follower ="";
		List<Way> followerList = getOutputWays();
		for (Way way : followerList) {
			follower += way.getId()+ " - ";
		}
		output += "Way " +currentWay.getId() + " ends on " + outputNode.getId() + " followed by " +follower;
		output+="\n----------------------------------------------------------------------";
		return output;
	}
	
	public List<Way> getOutputWays() {
		List<Way> follower = parser.isStartNodeOf(parser.getLastNode(currentWay), null);
		List<Way> output = new ArrayList<>();
		boolean hasRestriction;
		for (Way way : follower) {
			hasRestriction=false;
			if(parser.waystoRelation.containsKey(way.getId())){
				List<Long> relations = parser.waystoRelation.get(way.getId());
				for (Long index : relations) {
					Relation relation = parser.relationsMap.get(index);
					if(hasRestriction(way, relation)) {
						hasRestriction=true;
						System.out.println("Killed " + way.getId());
					}
				}
				if(!hasRestriction)
					output.add(way);
			}
			else
				output.add(way);
		}
		return output;
	}
	public boolean hasRestriction(Way to, Relation rel) {
		Collection<Tag> tags = rel.getTags();
		boolean hasRestrictionTag=false;
		boolean toWay =false;
		boolean toNode =false;
		for (Tag tag : tags) {
			if(tag.getValue().equals("restriction"))
				hasRestrictionTag=true;
		}
		if(hasRestrictionTag) {
			List<RelationMember> members = rel.getMembers();
			for (RelationMember relationMember : members) {
				if(relationMember.getMemberId()==to.getId()) {
					toWay= relationMember.getMemberRole().equals("to");
				}
				else if(relationMember.getMemberId()==outputNode.getId()) {
					toNode = relationMember.getMemberRole().equals("Via");
				}
			}
			return toWay&& toNode;
		}
		else
			return false;
	}
	
	public Way getCurrentWay() {
		if(currentWay == null) {
			List<Way> possibleWays;
			while (!nearestNodes.isEmpty() && currentWay == null) {
				possibleWays = parser.getWays((Node) nearestNodes.poll());
				for (Way way : possibleWays) {
					if(parser.isOneWay(way)) {
						Point crossRoadCenter = db.getCenter(crossroadID);
						if(crossRoadCenter.distance(parser.pointToNode(parser.getFirstNode(way)))<crossRoadCenter.distance(parser.pointToNode(parser.getLastNode(way)))) {
							continue;
						}
					}
					currentWay = way;
					break;
				}
			}
			/*if(parser.isStartNodeOf(parser.getLastNode(currentWay), currentWay).size()==1){
				currentWay= parser.followWay(currentWay, true);
			}*/
		}
		return currentWay;
		
	}
	
	public double getLon(){
		return lon;
	}
	public double getLat(){
		return lat;
	}
}
