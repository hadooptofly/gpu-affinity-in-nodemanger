/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Also created by yuanfeng.
 */

package org.apache.hadoop.yarn.server.nodemanager.containermanager.gpumanager;

import com.google.common.collect.Sets;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.service.AbstractService;
import org.apache.hadoop.util.Shell;
import org.apache.hadoop.yarn.server.nodemanager.Context;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GCoreManagerImpl extends AbstractService implements GCoreManager {

    private static final Log LOG = LogFactory.getLog(GCoreManagerImpl.class);

    private static final Comparator<SameCpuGroup> comparator = new Comparator<SameCpuGroup>() {
        @Override
        public int compare(SameCpuGroup o1, SameCpuGroup o2) {
            return Integer.compare(o1.getMemberSize(), o2.getMemberSize());
        }
    };

    private static final Comparator<SameCpuGroup> anti_comparator = new Comparator<SameCpuGroup>() {
        @Override
        public int compare(SameCpuGroup o1, SameCpuGroup o2) {
            return -(Integer.compare(o1.getMemberSize(), o2.getMemberSize()));
        }
    };

    private Context context;
    private Configuration conf;
    // all cores collection contain single and group ones
    private ArrayList<String> baseList;
    private Set<SameCpuGroup> groups;

    public GCoreManagerImpl(Context context) {
        super(GCoreManagerImpl.class.getName());
        this.baseList = new ArrayList<String>();
        this.groups = new TreeSet<SameCpuGroup>(comparator);
        this.context = context;
    }

    @Override
    protected void serviceInit(Configuration conf) throws Exception {
        this.conf = conf;
        LOG.info("GCoreManagerService is initing...");
        constructBaseGpuList();
        super.serviceInit(conf);
    }

    @Override
    protected void serviceStart() throws Exception {
        super.serviceStart();
    }

    @Override
    protected void serviceStop() throws Exception {
        super.serviceStop();
    }

    @Override
    public synchronized String aquireGCore(int num) throws Exception {
        // parse topo and rebuild groups
        parseTopo();
        LOG.info("Before assign: " + baseList);
        if (num == 1) {
            // just peek one from baselist
            // first need exclude the group
            // ones.
            ArrayList<String> copy = new ArrayList<String>(baseList);
            for (SameCpuGroup group :  groups) {
                copy.removeAll(group.getMemberList());
            }

            // leave single cores
            if (copy.size() != 0) {
                String candidate = copy.remove(0);
                // remove from baselist
                baseList.remove(candidate);
                LOG.info("After assign: " + baseList + " assigned a single core " + candidate);
                return candidate;
            } else {
                // There is no single ones
                // get 1 from groups
                if (groups.size() != 0) {
                    for (SameCpuGroup group : groups) {
                        for (String member : group.getMemberList()) {
                            baseList.remove(member);
                            LOG.info("After assign: " + baseList + " assigned a single core from a group " + member);
                            return member;
                        }
                    }
                } else {
                    LOG.warn("Maybe you config a bigger cores in this node, or cores isn`t be released.");
                    // throw new Exception("Maybe you config a bigger cores in this node, or cores isn`t be released." +
                    //         " and it is very serious.");
                    return "0";
                }
            }
        }

        // assign group directly if require count
        // is bigger or equal group size
        // (1,1,2) and require 2 so assign
        // (2)
        // or
        // (1,4,6) and require 3 so assign
        // (3) from 4
        for (SameCpuGroup group : groups) {
            if (num <= group.getMemberSize()) {
                StringBuilder sb = new StringBuilder();
                for (String member : group.getMemberList()) {
                    baseList.remove(member);
                    sb.append(member);
                    sb.append(",");
                    // do not need remove group object here
                    num--;
                    if (num == 0) {
                        String assigned = sb.toString().substring(0, sb.length() - 1);
                        LOG.info("After assign: " + baseList + " assigned group cores " + assigned);
                        return assigned;
                    }
                }
            }
        }

        // if there is no a group
        // which could fulfill the require
        // so begin compose the propose from
        // tail to head of the list
        // and if all groups could not fulfill
        // the require still, absorb single core
        // from baselist if have.
        ArrayList<SameCpuGroup> copy = new ArrayList<SameCpuGroup>(groups);
        Collections.sort(copy, anti_comparator);
        StringBuilder sb = new StringBuilder();
        out:
        for (SameCpuGroup group : copy) {
            if (group.getMemberSize() <= num) {
                baseList.removeAll(group.getMemberList());
                sb.append(group.getMembers());
                sb.append(",");
                num -= group.getMemberSize();
                if (num == 0)
                    break out;
            } else {
                // peek portion from group
                for (String member : group.getMemberList()) {
                    // remove from baselist
                    baseList.remove(member);
                    sb.append(member);
                    sb.append(",");
                    num--;
                    if (num == 0)
                        break out;
                }
            }
        }

        if (num == 0) {
            String assigned = sb.toString().substring(0, sb.length() - 1);
            LOG.info("After assign: " + baseList + " assigned multi-group cores " + assigned);
            return assigned;
        } else {
            // peek single core
            // but for race condition yarn-site.xml
            // configs a bigger num of gcore than real
            // or gcore do not release before.
            if (baseList.size() < num) {
                num = baseList.size();
                LOG.warn("Maybe you config a bigger cores in this node, or cores isn`t be released.");
            }
            for (int i = 0; i < num; i++) {
                sb.append(baseList.remove(0));
                sb.append(",");
            }

            if (sb.length() == 0) {
                // throw new Exception("Maybe you config a bigger cores in this node, or cores isn`t be released." +
                //         " and it is very serious.");
                return "0";
            }
            String assigned = sb.toString().substring(0, sb.length() - 1);
            LOG.info("After assign: " + baseList + " assigned multi-group and single cores " + assigned);
            return assigned;
        }
    }

    @Override
    public synchronized boolean releaseGCore(String cores) {
        // just add cores return to baselist
        baseList.addAll(Arrays.asList(cores.split(",")));
        LOG.info("Release cores:" + cores);
        return true;
    }

    private void constructBaseGpuList() throws Exception {
        // get gpus topo.
        String output = Shell.execCommand("/bin/sh navidia-smi topo --matrix");
        verfyOutput(output);

        // remove other useless info
        for (String line : output.split("\n")) {
            if (line.startsWith("GPU")) {
                String coreId = line.split("\t")[0];
                baseList.add(coreId.replace("GPU", ""));
                LOG.info("Add gpu core " + coreId);
            }
        }
    }

    private void parseTopo() throws Exception {
        // get gpus topo.
        String output = Shell.execCommand("/bin/sh navidia-smi topo --matrix");
        verfyOutput(output);

        // remove other useless info
        StringBuilder sb = new StringBuilder();
        for (String line : output.split("\n")) {
            if (line.startsWith("GPU")) {
                sb.append(line);
                sb.append("\n");
            }
        }

        // build tuple relations such as
        // 0-1 0-2 1-2
        Set<String> tuples = Sets.newTreeSet();
        int rowNum = 0;
        for (String sline : sb.toString().split("\n")) {
            String[] cells = sline.split("\t");
            for (int colNum = 1; colNum < cells.length - 1; colNum++) {
                if (!"SOC".equals(cells[colNum].trim()) && !"X".equals(cells[colNum].trim())) {
                    if (baseList.contains(rowNum + "")
                            && baseList.contains((colNum - 1) + "")) {
                        if (rowNum > colNum - 1) {
                            tuples.add((colNum - 1) + "-" + rowNum);
                        } else {
                            tuples.add(rowNum + "-" + (colNum - 1));
                        }
                    }
                }
            }
            rowNum++;
        }

        // re-build the groups
        groups.clear();

        next:
        for (String tuple : tuples) {
            for (SameCpuGroup group : groups) {
                for (String id : tuple.split("-")) {
                    if (group.isMember(id)) {
                        group.addMember(tuple.split("-")[1]);
                        continue next;// go to next tuple
                    }
                }
            }
            SameCpuGroup group = new SameCpuGroup(tuple);
            groups.add(group);
        }
    }

    private void verfyOutput(String output) throws Exception {
        // just simple check manner
        Pattern pattern = Pattern.compile("Legend:");
        Matcher matcher = pattern.matcher(output);
        if (!matcher.find()) {
            throw new InvalidParameterException("Output do not contain valid topo.");
        }
    }
}
