import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.cluster.sharding.ShardRegion;
import akka.japi.pf.ReceiveBuilder;
import akka.persistence.AbstractPersistentActor;

/**
 * Reproducer of excessive amount of 'createIndexes' commands with cluster sharding and MongoDB Akka persistence plugin.
 * <p>
 * 40000 messages (integers 0 to 39999) are sent to 200 persistent actors in 20 shards. Each persistence actor persists
 * all messages it receives.
 */
public class Main {

    public static final int SHARDS = 20;

    public static final int ACTORS_PER_SHARD = 10;

    public static final int EVENTS_PER_ACTOR = 200;

    public static final int TOTAL_ACTORS = ACTORS_PER_SHARD * SHARDS; // 200

    public static final int TOTAL_EVENTS = EVENTS_PER_ACTOR * TOTAL_ACTORS; // 40000

    public static void main(final String... args) {

        // create actor system with name "createIndexes"
        final String systemName = "createIndexes";
        final Config config = ConfigFactory.load();
        final ActorSystem system = ActorSystem.create(systemName, config);

        // start an akka cluster with the actor system
        final Address address = Address.apply("akka.tcp", systemName);
        Cluster.get(system).joinSeedNodes(Collections.singletonList(address));

        // create a shard region for the persistent actors
        final Props props = Props.create(PersistentActor.class, PersistentActor::new);
        final ClusterShardingSettings settings = ClusterShardingSettings.create(system);
        final ActorRef shardRegion = ClusterSharding.get(system)
                .start("createIndexes", props, settings, new MessageExtractor());

        // send integers 0 to 39999 to the shard region
        for (int i = 0; i < TOTAL_EVENTS; ++i) {
            shardRegion.tell(i, null);
        }
    }
}

/**
 * A persistent actor that persists all messages it receives.
 */
final class PersistentActor extends AbstractPersistentActor {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String pid = getSelf().path().name();

    @Override
    public String persistenceId() {
        return pid;
    }

    @Override
    public Receive createReceiveRecover() {
        return ReceiveBuilder.create()
                .matchAny(x -> {})
                .build();
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .matchAny(message ->
                        persist(message.toString(),
                                e -> logger.info("actor {} persisted event {}.", pid, message)))
                .build();
    }
}

/**
 * A message extractor that distributes integer messages across
 */
final class MessageExtractor implements ShardRegion.MessageExtractor {

    @Override
    public String entityId(final Object message) {
        return String.valueOf((Integer) message % Main.TOTAL_ACTORS);
    }

    @Override
    public Object entityMessage(final Object message) {
        return message;
    }

    @Override
    public String shardId(final Object message) {
        return String.valueOf((Integer) message % Main.SHARDS);
    }
}
