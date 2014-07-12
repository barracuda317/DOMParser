import java.util.Comparator;

import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.postgis.Point;


public class NodesComparator implements Comparator{

	private Point reference;

	public NodesComparator(Point reference) {
		this.reference = reference;
	}
	@Override
	public int compare(Object o1, Object o2) {
		Node n1 = (Node) o1;
		Node n2 = (Node) o2;
		if((distance(n2)-distance(n1))>0){
			return -1;
		}
		else
			return 1;
	}
	/*
	public double distance(Node node) {
		Point nodePoint = new Point(node.getLatitude(), node.getLongitude());
		return nodePoint.distance(reference);
	}
	*/
	public double distance(Node node){
		double dx = 71.5* (node.getLongitude()-reference.getY());
		double dy = 111.3* (node.getLatitude()-reference.getX());
		return Math.sqrt(dx*dx+dy*dy);
	}
}
