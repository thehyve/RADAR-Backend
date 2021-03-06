package org.radarcns.sink.mongoDB;

import com.mongodb.MongoClient;
import com.mongodb.MongoCredential;
import com.mongodb.MongoException;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoDatabase;

import org.bson.Document;
import org.radarcns.consumer.ConsumerRadar;
import org.radarcns.sink.SinkRadar;
import org.radarcns.util.RadarConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Francesco Nobilia on 10/10/2016.
 */


public class MongoDBSinkRadar extends SinkRadar{

    private final static Logger log = LoggerFactory.getLogger(MongoDBSinkRadar.class);

    /*
     * The number of MongoClient must be limited since it is a connection pool (BP singleton).
     * The singleton is safe to be used by multiple threads
     */
    private MongoClient mongoClient;
    private MongoDatabase database;

    private RadarConfig radarConfig;

    private String clientID = "MongoDBSinkRadar";

    public MongoDBSinkRadar() throws InvalidParameterException {
        super(new RadarConfig().getMongoDBThreads(), "MongoDBSinkRadar");
        initSink(null);
    }

    public MongoDBSinkRadar(int numThreads) throws InvalidParameterException {
        super(numThreads, "MongoDBSinkRadar");
        initSink(null);
    }

    public MongoDBSinkRadar(String poolName) throws InvalidParameterException {
        super(new RadarConfig().getMongoDBThreads(), ((poolName == null) || (poolName.isEmpty()) ? "MongoDBSinkRadar" : poolName));
        initSink(poolName);
    }

    public MongoDBSinkRadar(int numThreads, String poolName) throws InvalidParameterException {
        super(numThreads, ((poolName == null) || (poolName.isEmpty()) ? "MongoDBSinkRadar" : poolName));
        initSink(poolName);
    }

    private void initSink(String clientID){
        log.trace("InitSink");
        radarConfig = new RadarConfig();

        this.clientID = ((clientID==null) || (clientID.isEmpty()) ? "MongoDBSinkRadar" : clientID);

        initMongoDBConnection();

        initiWorkers();
    }

    private MongoDatabase initMongoDBConnection() throws IllegalStateException{
        log.trace("initMongoDBConnection");
        String dbName = radarConfig.getMongoDbDatabaseName();

        List<ServerAddress> seeds = new ArrayList<ServerAddress>();
        seeds.add(new ServerAddress(radarConfig.getMongoDbServer()));
        List<MongoCredential> credentials = new ArrayList<MongoCredential>();
        credentials.add(
                MongoCredential.createMongoCRCredential(
                        radarConfig.getMongoDbUsr(),
                        dbName,
                        radarConfig.getMongoDbPwd()
                )
        );
        mongoClient = new MongoClient(seeds,credentials);

        if(checkMongoConnection()){
            database = mongoClient.getDatabase(dbName);
            return database;
        }
        else{
            throw new IllegalStateException("MongoDB server cannot be reached");
        }
    }

    @Override
    public ConsumerRadar createConsumer() {
        return new MongoDBConsumerALO(clientID,database);
    }

    @Override
    public void shutdown() throws InterruptedException{
        super.shutdown();
        mongoClient.close();
    }

    private boolean checkMongoConnection(){
        try {
            mongoClient.getDatabase("admin").runCommand(new Document("ping", 1));
            log.trace("Connected to MongoDB");
            return true;
        } catch (MongoException e) {
            mongoClient.close();
            log.error("MongoDB server cannot be reached",e);
        }

        return false;
    }
}
