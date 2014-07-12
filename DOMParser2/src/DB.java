import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.postgis.Point;


public class DB {
	private static Connection con = null;
	private static String dbHost = "212.72.183.108"; // Hostname
	private static String dbPort = "3306";      // Port -- Standard: 3306
	private static String dbName = "TrafficDataWarehouse";   // Datenbankname
	private static String dbUser = "dasense";     // Datenbankuser
	private static String dbPass = "dasenseopendataserver";      // Datenbankpasswort
	private OSMiumParser parser;

	public DB(OSMiumParser parser){
		this.parser=parser;
		try {
	        Class.forName("com.mysql.jdbc.Driver"); // Datenbanktreiber für JDBC Schnittstellen laden.
	 
	        // Verbindung zur JDBC-Datenbank herstellen.
	        con = DriverManager.getConnection("jdbc:mysql://"+dbHost+":"+dbPort+"/"+dbName,dbUser,dbPass);
	    } catch (ClassNotFoundException e) {
	        System.out.println("Treiber nicht gefunden");
	    } catch (SQLException e) {
	        System.out.println("Verbindung nicht moglich");
	        System.out.println("SQLException: " + e.getMessage());
	        System.out.println("SQLState: " + e.getSQLState());
	        System.out.println("VendorError: " + e.getErrorCode());
	    }
		System.out.println("Verbindung zur Datenbank hergestellt");
	}
	
	public void getTables() throws SQLException {
		DatabaseMetaData md = con.getMetaData();
		ArrayList<String> tablenames = new ArrayList<>();
	    ResultSet rs = md.getTables(null, null, "%", null);
	    while (rs.next()) {
	      tablenames.add(rs.getString(3));
	    }
	    
	}
	
	public ArrayList<Sensor> getSensors() {
		ArrayList<Sensor> sensorlist = new ArrayList<>();
		try {
			String selectStmt = "SELECT * FROM jee_crmodel_SensorDim";
			Statement stmt = con.createStatement();
			ResultSet rs = stmt.executeQuery(selectStmt);
			while(rs.next()) {
				if(rs.getString("REALNAME").startsWith("D"))
					sensorlist.add(new Sensor(rs.getInt("ID"), rs.getDouble("LAT"),rs.getDouble("LON"), rs.getString("REALNAME"), rs.getInt("CROSSROAD_ID") ,parser,this));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return sensorlist;
	}
	public ArrayList<Sensor> getSensors(int crossroad) {
		ArrayList<Sensor> sensorlist = new ArrayList<>();
		try {
			String selectStmt = "SELECT * FROM jee_crmodel_SensorDim WHERE CROSSROAD_ID = " +crossroad +" AND NOT LAT IS NULL AND NOT LON IS NULL";
			Statement stmt = con.createStatement();
			ResultSet rs = stmt.executeQuery(selectStmt);
			while(rs.next()) {
				if(rs.getString("REALNAME").startsWith("D"))
					sensorlist.add(new Sensor(rs.getInt("ID"), rs.getDouble("LAT"),rs.getDouble("LON"), rs.getString("REALNAME"), rs.getInt("CROSSROAD_ID"),parser,this ));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return sensorlist;
	}
	
	public void closeConnection(){
		try {
			con.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	

	public String getCrossroadName(int crossroadID) {
		String selectStmt = "SELECT REALNAME FROM jee_crmodel_CrossroadDim WHERE ID = " + crossroadID;
		Statement stmt;
		try {
			stmt = con.createStatement();
			ResultSet rs = stmt.executeQuery(selectStmt);
			while(rs.next()){
				return rs.getString("REALNAME");
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return "Not Found";
	}
	
	public String getSensorName(int sensorID) {
		String selectStmt = "SELECT REALNAME FROM jee_crmodel_SensorDim WHERE ID = " + sensorID;
		Statement stmt;
		try {
			stmt = con.createStatement();
			ResultSet rs = stmt.executeQuery(selectStmt);
			while(rs.next()){
				return rs.getString("REALNAME");
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return "Not Found";
		
	}
	
	public Point getCenter(int crossroadID) {
		String selectStmt = "SELECT LAT,LON FROM jee_crmodel_CrossroadDim WHERE ID = " + crossroadID;
		Statement stmt;
		try {
			stmt = con.createStatement();
			ResultSet rs = stmt.executeQuery(selectStmt);
			while(rs.next()){
				double lat = rs.getDouble("LAT");
				double lon = rs.getDouble("LON");
				return new Point(lat,lon);
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	public boolean isValid(int crossroadID) {
		String selectStmt = "SELECT VALIDFROM,VALIDTO FROM jee_crmodel_CrossroadDim WHERE ID = " + crossroadID;
		Statement stmt;
		try {
			stmt = con.createStatement();
			ResultSet rs = stmt.executeQuery(selectStmt);
			while(rs.next()){
				Date validfrom = rs.getDate("VALIDFROM");
				return rs.getDate("VALIDTO") == null && validfrom != null && rs.getDate("VALIDFROM").before(new Date());
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}
	
	public boolean writeCrossroad(Crossroad crossroad) {
		if(crossroad == null)
			return false;
		List<Way> inputWays = crossroad.getInputWays();
		List<Way> outputWays = crossroad.getOutputWays();
		Way way;
		try {
			String inserTableSQL = "INSERT IGNORE INTO mw_CrossroadWays VALUES (?,?,?,DEFAULT)";
			PreparedStatement s = con.prepareStatement(inserTableSQL);
			for (int i = 0; i<inputWays.size() && inputWays!=null;i++) {
				way = inputWays.get(i);
				if(way==null)
					continue;
				s.setInt(1, crossroad.id);
				s.setLong(2, way.getId());
				s.setInt(3, 0);
				s.executeUpdate();
			}
			for (int i = 0; i<outputWays.size() && inputWays!=null;i++) {
				way = outputWays.get(i);
				if(way==null)
					continue;
				s.setInt(1, crossroad.id);
				s.setLong(2, way.getId());
				s.setInt(3, 1);
				s.executeUpdate();
			}
			s.executeBatch();
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	public Crossroad importCrossroad(int crossroadID) {
		List<Way> inputWays = new ArrayList<>();
		List<Way> outputWays = new ArrayList<>();
		String selectStmt = "SELECT * FROM mw_CrossroadWays WHERE CROSSROAD_ID = " + crossroadID;
		Statement stmt;
		try {
			stmt = con.createStatement();
			ResultSet rs = stmt.executeQuery(selectStmt);
			while(rs.next()){
				if(rs.getInt("TYPE")==0)
					inputWays.add(parser.getWay(rs.getLong("OSMWAY_ID")));
				else
					outputWays.add(parser.getWay(rs.getLong("OSMWAY_ID")));
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return new Crossroad(getSensors(crossroadID), crossroadID ,inputWays,outputWays, parser, this);
	}

	public boolean isAlreadyConnected(Crossroad from,Crossroad to) {
		String selectStmt = "SELECT * FROM mw_CrossroadConnection WHERE FROM_CROSSROAD = \"" + from.id+ " \" AND TO_CROSSROAD= \"" +to.id+"\"";
		Statement stmt;
		try {
			stmt = con.createStatement();
			ResultSet rs = stmt.executeQuery(selectStmt);
			return rs.first();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}

	public boolean connectionDenied(Crossroad from, Crossroad to) {
		String selectStmt = "SELECT * FROM mw_CrossroadConnectionDenied WHERE FROM_CROSSROAD = \"" + from.id+ " \" AND TO_CROSSROAD= \"" +to.id+"\"";
		Statement stmt;
		try {
			stmt = con.createStatement();
			ResultSet rs = stmt.executeQuery(selectStmt);
			return rs.first();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}
	
	public boolean insertConnection(Crossroad from, Way outWay,Crossroad to, Way inWay) {
		if(from == null || outWay == null || to==null || inWay ==null)
			return false;
		try {
			String inserTableSQL = "INSERT INTO mw_CrossroadConnection VALUES (?,?,?,?,DEFAULT,DEFAULT)";
			PreparedStatement s = con.prepareStatement(inserTableSQL);
			s.setInt(1, from.id);
			s.setLong(2, outWay.getId());
			s.setInt(3, to.id);
			s.setLong(4, inWay.getId());
			s.executeUpdate();
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
}
