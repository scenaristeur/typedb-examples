/*
 * Copyright (C) 2022 Vaticle
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.vaticle.typedb.example.xcom;

import com.vaticle.typedb.client.TypeDB;
import com.vaticle.typedb.client.api.TypeDBClient;
import com.vaticle.typedb.client.api.TypeDBSession;
import com.vaticle.typedb.client.api.TypeDBTransaction;
import com.vaticle.typeql.lang.TypeQL;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class XCOMTest {

    TypeDBClient client;
    TypeDBSession session;
    String databaseName = "xcom_test";

    @Before
    public void migrateDatabase() throws IOException {
        Migration.main(new String[]{databaseName});
        client = TypeDB.coreClient("localhost:1729");
        session = client.session(databaseName, TypeDBSession.Type.DATA);
    }

    @After
    public void deleteDatabase() {
        session.close();
        client.close();
    }

    @Test
    public void testMigration() {
        assertMigrationResults();
    }

    @Test
    public void testQueries() {
        Queries.databaseName = databaseName;

        // Start a new campaign, named Gatecrasher
        Queries.askQuestions(new String[] { String.valueOf(Queries.START_NEW_CAMPAIGN), "Gatecrasher", "0" });

        final String gatecrasher = "1";

        // Fetch available research projects in Gatecrasher
        Queries.askQuestions(new String[] { String.valueOf(Queries.GET_AVAILABLE_RESEARCH), gatecrasher, "0" });
        assertEquals(1, Queries.answers.size());
        List<ResearchTask> researchTasks = (List<ResearchTask>) Queries.answers.get(0).getEntity();
        // Check that we have exactly 5 research projects available
        assertEquals(5, researchTasks.size());
        ResearchTask alienBiotech = null;
        int alienBiotechKey = -1;
        for (int key = 1; key <= researchTasks.size(); key++) {
            ResearchTask rt = researchTasks.get(key - 1);
            if (rt.name.equals("Alien Biotech")) {
                alienBiotech = rt;
                alienBiotechKey = key;
            }
        }
        // Check that Alien Biotech is available to research
        assertNotNull(alienBiotech);
        assertNotEquals(-1, alienBiotechKey);

        // View Gatecrasher's inventory
        Queries.askQuestions(new String[] { String.valueOf(Queries.VIEW_INVENTORY), gatecrasher, "0" });
        assertEquals(2, Queries.answers.size());
        List<InventoryItem> inventory = (List<InventoryItem>) Queries.answers.get(1).getEntity();
        // Check that the inventory is empty
        assertEquals(0, inventory.size());

        // Research Alien Biotech
        Queries.askQuestions(new String[] { String.valueOf(Queries.ADVANCE_RESEARCH), gatecrasher, String.valueOf(alienBiotechKey), "0" });

        // Fetch the research projects that are now available
        Queries.askQuestions(new String[] { String.valueOf(Queries.GET_AVAILABLE_RESEARCH), gatecrasher, "0" });
        assertEquals(3, Queries.answers.size());
        researchTasks = (List<ResearchTask>) Queries.answers.get(2).getEntity();
        // Check that, after researching Alien Biotech, we have only 4 research projects available
        assertEquals(4, researchTasks.size());

        // Fetch all items
        Queries.askQuestions(new String[] { String.valueOf(Queries.LIST_ALL_ITEMS), "0" });
        assertEquals(4, Queries.answers.size());
        List<String> items = (List<String>) Queries.answers.get(3).getEntity();
        int sectoidCorpseKey = -1;
        for (int key = 1; key <= items.size(); key++) {
            String item = items.get(key - 1);
            if (item.equals("Sectoid Corpse")) {
                sectoidCorpseKey = key;
            }
        }
        assertNotEquals(-1, sectoidCorpseKey);

        // Acquire one Sectoid Corpse
        Queries.askQuestions(new String[] { String.valueOf(Queries.ACQUIRE_ITEM), gatecrasher, "Sectoid Corpse", "1", "0" });

        // Fetch the research projects that are now available
        Queries.askQuestions(new String[] { String.valueOf(Queries.GET_AVAILABLE_RESEARCH), gatecrasher, "0" });
        assertEquals(5, Queries.answers.size());
        researchTasks = (List<ResearchTask>) Queries.answers.get(4).getEntity();
        // Check that, after acquiring a Sectoid Corpse, a new research project has been unlocked
        assertEquals(5, researchTasks.size());
        ResearchTask sectoidAutopsy = null;
        for (int key = 1; key <= researchTasks.size(); key++) {
            ResearchTask rt = researchTasks.get(key - 1);
            if (rt.name.equals("Sectoid Autopsy")) {
                sectoidAutopsy = rt;
            }
        }
        // Check that the newly unlocked research project is Sectoid Autopsy
        assertNotNull(sectoidAutopsy);

        // Fetch all techs
        Queries.askQuestions(new String[] { String.valueOf(Queries.LIST_ALL_TECHS), "0" });
        assertEquals(6, Queries.answers.size());
        List<String> techs = (List<String>) Queries.answers.get(5).getEntity();
        int gaussWeaponsKey = -1;
        for (int key = 1; key <= techs.size(); key++) {
            String tech = techs.get(key - 1);
            if (tech.equals("Gauss Weapons")) {
                gaussWeaponsKey = key;
            }
        }
        // Check that Gauss Weapons is a tech
        assertNotEquals(-1, gaussWeaponsKey);

        // Fetch the tree of prerequisite techs that must be researched in order to unlock Gauss Weapons
        Queries.askQuestions(new String[] { String.valueOf(Queries.COMPUTE_TECH_REQUIREMENTS), String.valueOf(gaussWeaponsKey), "0" });
        assertEquals(7, Queries.answers.size());
        Set<String> techRequirements = (Set<String>) Queries.answers.get(6).getEntity();
        // Check that the prerequisites are [Modular Weapons, Magnetic Weapons]
        assertEquals(2, techRequirements.size());
        assertTrue(techRequirements.contains("Modular Weapons"));
        assertTrue(techRequirements.contains("Magnetic Weapons"));
    }

    public void assertMigrationResults() {
        final TypeDBTransaction transaction = session.transaction(TypeDBTransaction.Type.READ);

        final int totalTechs = transaction.query().match(TypeQL.parseQuery("match $x isa research-project; get $x; count;").asMatchAggregate()).get().asNumber().intValue();
        assertEquals(46, totalTechs);

        final int totalItems = transaction.query().match(TypeQL.parseQuery("match $x isa item; get $x; count;").asMatchAggregate()).get().asNumber().intValue();
        assertEquals(34, totalItems);

        final int totalResearchTechRequirements = transaction.query().match(TypeQL.parseQuery("match $x isa tech-requirement-to-begin-research; get $x; count;").asMatchAggregate()).get().asNumber().intValue();
        assertEquals(36, totalResearchTechRequirements);

        final int totalResearchResourceCosts = transaction.query().match(TypeQL.parseQuery("match $x isa resource-cost-to-begin-research; get $x; count;").asMatchAggregate()).get().asNumber().intValue();
        assertEquals(44, totalResearchResourceCosts);

        transaction.close();
    }
}
