package worker;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.exceptions.JedisConnectionException;
import java.sql.*;

import org.json.JSONObject;
import argo.jdom.JdomParser;
import argo.jdom.JsonNode;
import argo.jdom.JsonRootNode;

class Worker {
  public static void main(String[] args) {
    try {
      /*Removing to change to connecting services in PCF
       * Jedis redis = connectToRedis("redis");
       * Connection dbConn = connectToDB("db");*/
   
      String vcap_services = System.getenv("VCAP_SERVICES");
      JsonNode postgreSQLcredentials = null;
      JsonNode rediscredentials = null;
      JsonNode p_rediscredentials = null;
      JsonNode mySQLCredentials = null;
      
      Connection dbConn = null;
      Jedis redis = null;
      
      if (vcap_services != null && vcap_services.length() > 0) {
    	  
    	  JsonRootNode root = new JdomParser().parse(vcap_services);
    	  
    	  // parsing rediscloud credentials
    	  try{
    		  
    		  JsonNode rediscloudNode = root.getNode("rediscloud");
              rediscredentials = rediscloudNode.getNode(0).getNode("credentials");
          }
    	  catch (IllegalArgumentException ex){
    		  
    		  JsonNode rediscloudNode = root.getNode("p-redis");
    		  p_rediscredentials = rediscloudNode.getNode(0).getNode("credentials");
    	  }
          
          
          //get postgreSQL credentials
          try{
        	  JsonNode postgreSQLcloudNode = root.getNode("elephantsql");
              postgreSQLcredentials = postgreSQLcloudNode.getNode(0).getNode("credentials");
          }
          catch (IllegalArgumentException ex){
        	 //nothing happends, we just won't use postgreSQL connection 
          }
          
          //get a mySQl credential 
          try{
        	  
        	  JsonNode postgreSQLcloudNode = root.getNode("p-mysql");
        	  mySQLCredentials = postgreSQLcloudNode.getNode(0).getNode("credentials");
          }
          catch (IllegalArgumentException ex)
          {
        	  //nothing wrong with not catching some creds for database
          }
          
          
       
      }
      if (rediscredentials != null){
    	  redis = connectToRedis(rediscredentials);       
      }
      else{
    	  redis = connectToPivotalRedis(p_rediscredentials);   
      }
    	  
      
      //determine which dataservice we'll use. Will default to postgreSQL
      if (postgreSQLcredentials != null){
    	  dbConn = connectToPostgreDB(postgreSQLcredentials);
    	  System.out.println("Connected to Redis on PWS");	
      }
      else if (mySQLCredentials != null){
    	  dbConn = connectToMySQLDB(mySQLCredentials);
    	  System.out.println("Connected to P-Redis on PCF");
      }
      	


      System.out.println("Watching vote queue to see what comes in");
      
      while (true) {
        String voteJSON = redis.blpop(0, "votes").get(1);
        JSONObject voteData = new JSONObject(voteJSON);
        String voterID = voteData.getString("voter_id");
        String vote = voteData.getString("vote");

        System.err.printf("Processing vote for '%s' by '%s'\n", vote, voterID);
        updateVote(dbConn, voterID, vote);
      }
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  static void updateVote(Connection dbConn, String voterID, String vote) throws SQLException {
    PreparedStatement insert = dbConn.prepareStatement(
      "INSERT INTO votes (id, vote) VALUES (?, ?)");
    insert.setString(1, voterID);
    insert.setString(2, vote);

    try {
      insert.executeUpdate();
    } catch (SQLException e) {
      PreparedStatement update = dbConn.prepareStatement(
        "UPDATE votes SET vote = ? WHERE id = ?");
      update.setString(1, vote);
      update.setString(2, voterID);
      update.executeUpdate();
    }
  }

  static Jedis connectToRedis(JsonNode credentials) {
      JedisPool pool = new JedisPool(new JedisPoolConfig(),
              credentials.getStringValue("hostname"),
              Integer.parseInt(credentials.getStringValue("port")),
              Protocol.DEFAULT_TIMEOUT,
              credentials.getStringValue("password"));
    return pool.getResource();
  }
  
  static Jedis connectToPivotalRedis(JsonNode credentials) {
	  JedisPool pool = new JedisPool(new JedisPoolConfig(),
              credentials.getStringValue("host"),
              Integer.parseInt(credentials.getNumberValue("port")),
              Protocol.DEFAULT_TIMEOUT,
              credentials.getStringValue("password"));
    return pool.getResource();
  }
  
  static Connection connectToMySQLDB (JsonNode credentials) throws SQLException{
	  Connection conn = null;
	  
	  try {
		  Class.forName("com.mysql.jdbc.Driver");
	  }
	  catch (ClassNotFoundException e) {
      System.out.println("Class not found " + e);
	  }
	  
	  String url = credentials.getStringValue("jdbcUrl");
	  
	  while (conn == null) {
	        try {
	          conn = DriverManager.getConnection(url);
	          System.out.println("connected to db");
	        	
	        } catch (SQLException e) {
	          System.err.println("Failed to connect to db - retrying " + e.toString());
	          sleep(1000);
	        }
	  }
	  
	  PreparedStatement st = conn.prepareStatement(
		        "CREATE TABLE IF NOT EXISTS votes (id VARCHAR(255) NOT NULL UNIQUE, vote VARCHAR(255) NOT NULL)");
		      st.executeUpdate();
	 
	 return conn;
	  
  }

  static Connection connectToPostgreDB(JsonNode credentials) throws SQLException {
    Connection conn = null;
    
    try {
    	Class.forName("org.postgresql.Driver");
    } catch (ClassNotFoundException e) {
        System.out.println("Class not found " + e);
    }
    
    //parse out credentials from URI string
    String uri = credentials.getStringValue("uri");
    String host = uri.substring(uri.lastIndexOf("@") + 1 , uri.length());
    //username and password parse:
    String userName = uri.substring(uri.indexOf("://") + 3 , uri.length());
    String passWord = userName;
    userName = userName.substring(0, userName.indexOf(":"));
    passWord = passWord.substring(passWord.indexOf(":") +1 , passWord.indexOf("@"));
    
      while (conn == null) {
        try {
          conn = DriverManager.getConnection("jdbc:postgresql://" + host, userName, passWord);
          System.out.println("connected to db");
        	
        } catch (SQLException e) {
          System.err.println("Failed to connect to db - retrying " + e.toString());
          sleep(1000);
        }
      }

      PreparedStatement st = conn.prepareStatement(
        "CREATE TABLE IF NOT EXISTS votes (id VARCHAR(255) NOT NULL UNIQUE, vote VARCHAR(255) NOT NULL)");
      st.executeUpdate();

    return conn;
  }

  static void sleep(long duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException e) {
      System.exit(1);
    }
  }
}
