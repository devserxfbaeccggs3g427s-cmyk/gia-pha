package vn.giapha.research.sharing.domain.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Allowlisted public projection of a shared tree (Task 33.3, Req 11.4-11.5).
 * Only fields explicitly enumerated here are allowed to leave the server in
 * response to an unauthenticated share-token holder — membership, owner,
 * email, address, and raw blob URLs are deliberately absent so a leaked
 * token cannot enumerate the underlying PII.
 */
public record PublicTreeView(
        String treeName,
        String treeDescription,
        String generatedAt,
        List<PublicMember> members,
        List<PublicRelationship> relationships,
        List<PublicEvent> events) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("treeName", treeName);
        map.put("treeDescription", treeDescription);
        map.put("generatedAt", generatedAt);
        map.put("members", members.stream().map(PublicMember::toMap).toList());
        map.put("relationships", relationships.stream().map(PublicRelationship::toMap).toList());
        map.put("events", events.stream().map(PublicEvent::toMap).toList());
        return map;
    }

    public record PublicMember(
            String externalId,
            String fullName,
            String nickname,
            Integer generation,
            String gender,
            boolean alive,
            Integer birthYear) {

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", externalId);
            map.put("fullName", fullName);
            map.put("nickname", nickname);
            map.put("generation", generation);
            map.put("gender", gender);
            map.put("alive", alive);
            map.put("birthYear", birthYear);
            return map;
        }
    }

    public record PublicRelationship(
            String externalId,
            String sourceMemberExternalId,
            String targetMemberExternalId,
            String type) {

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", externalId);
            map.put("sourceMemberId", sourceMemberExternalId);
            map.put("targetMemberId", targetMemberExternalId);
            map.put("type", type);
            return map;
        }
    }

    public record PublicEvent(
            String externalId,
            String title,
            String date,
            String type) {

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", externalId);
            map.put("title", title);
            map.put("date", date);
            map.put("type", type);
            return map;
        }
    }
}
