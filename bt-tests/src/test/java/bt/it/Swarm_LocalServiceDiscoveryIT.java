/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bt.it;

import bt.it.fixture.BaseBtTest;
import bt.it.fixture.SharedTrackerModule;
import bt.it.fixture.Swarm;
import bt.peer.lan.LocalServiceDiscoveryModule;
import bt.protocol.crypto.EncryptionPolicy;
import bt.runtime.BtClient;
import bt.runtime.Config;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;

public class Swarm_LocalServiceDiscoveryIT extends BaseBtTest {

    private static final int NUMBER_OF_SEEDERS = 5;

    private static final Config CONFIG = new Config() {
        @Override
        public Duration getTrackerQueryInterval() {
            return Duration.ofSeconds(5);
        }

        @Override
        public EncryptionPolicy getEncryptionPolicy() {
            return EncryptionPolicy.REQUIRE_PLAINTEXT;
        }
    };

    @Rule
    public Swarm swarm = buildSwarm()
            .config(CONFIG)
            .module(new LocalServiceDiscoveryModule())
            .seeders(NUMBER_OF_SEEDERS)
            .leechers(NUMBER_OF_SEEDERS)
            // not adding the SharedTrackerModule: LSD is the only source of peers
            .useInMemoryFileSystem()
            .build();

    @After
    public void after() {
        // TODO: workaround to shutdown swarm _before_ BaseBtTest removes files;
        // need to come up with something better to not write this everywhere
        swarm.shutdown();
    }

    @Test
    public void testSwarm_OneSeederOneLeecher() {
        BtClient seeder = swarm.getSeederHandles().iterator().next();
        BtClient leecher = swarm.getLeecherHandles().iterator().next();

        seeder.startAsync();
        leecher.startAsync().join();
        seeder.stop();

        assertEquals(NUMBER_OF_SEEDERS + 1, swarm.getSeeders().size());
        assertEquals(NUMBER_OF_SEEDERS - 1, swarm.getLeechers().size());
    }

    @Test
    public void testSwarm_ManySeedersOneLeecher() {
        List<BtClient> seeders = swarm.getSeederHandles();
        BtClient leecher = swarm.getLeecherHandles().iterator().next();

        seeders.forEach(BtClient::startAsync);
        leecher.startAsync().join();
        seeders.forEach(BtClient::stop);

        assertEquals(NUMBER_OF_SEEDERS + 1, swarm.getSeeders().size());
        assertEquals(NUMBER_OF_SEEDERS - 1, swarm.getLeechers().size());
    }

    @Test
    public void testSwarm_OneSeederManyLeechers() {
        BtClient seeder = swarm.getSeederHandles().iterator().next();
        List<BtClient> leechers = swarm.getLeecherHandles();

        CompletableFuture<?>[] leecherFutures =
                leechers.stream().map(BtClient::startAsync).toArray(CompletableFuture<?>[]::new);

        seeder.startAsync();
        CompletableFuture.allOf(leecherFutures).join();
        seeder.stop();

        assertEquals(NUMBER_OF_SEEDERS * 2, swarm.getSeeders().size());
        assertEquals(0, swarm.getLeechers().size());
    }

    @Test
    public void testSwarm_ManySeedersManyLeechers() {
        List<BtClient> seeders = swarm.getSeederHandles();
        List<BtClient> leechers = swarm.getLeecherHandles();

        CompletableFuture<?>[] leecherFutures =
                leechers.stream().map(BtClient::startAsync).toArray(CompletableFuture<?>[]::new);

        seeders.forEach(BtClient::startAsync);
        CompletableFuture.allOf(leecherFutures).join();
        seeders.forEach(BtClient::stop);

        assertEquals(NUMBER_OF_SEEDERS * 2, swarm.getSeeders().size());
        assertEquals(0, swarm.getLeechers().size());
    }
}