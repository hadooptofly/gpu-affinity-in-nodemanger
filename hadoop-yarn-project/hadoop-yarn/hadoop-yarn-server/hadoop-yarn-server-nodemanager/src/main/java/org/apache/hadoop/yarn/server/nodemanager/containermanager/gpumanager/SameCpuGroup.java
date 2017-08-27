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

import java.util.Set;
import java.util.TreeSet;

public class SameCpuGroup {
    private StringBuilder members;
    private Set<String> memberlist;

    public SameCpuGroup(String tuple) {
        members = new StringBuilder();
        memberlist = new TreeSet<String>();
        for (String member : tuple.split("-")) {
            members.append(member);
            members.append(",");
            memberlist.add(member);
        }
    }

    public void addMember(String member) {
            members.append(member);
            memberlist.add(member);
    }

    public int getMemberSize() {
        return memberlist.size();
    }

    public String getMembers() {
        return members.toString().substring(0, members.length() - 1);
    }

    public Set<String> getMemberList() { return memberlist; }

    public boolean isMember(String unCheck) {
        return memberlist.contains(unCheck);
    }

    @Override
    public String toString() {
        return members.toString().substring(0, members.length() - 1);
    }
}
