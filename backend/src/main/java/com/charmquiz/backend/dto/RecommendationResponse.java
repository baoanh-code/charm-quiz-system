package com.charmquiz.backend.dto;

public class RecommendationResponse {

    private String source;
    private boolean matched;
    private CharmData charm;
    private String matchedField;
    private String matchedValue;
    private Integer priority;

    public RecommendationResponse() {
    }

    public RecommendationResponse(
            String source,
            boolean matched,
            CharmData charm,
            String matchedField,
            String matchedValue,
            Integer priority) {

        this.source = source;
        this.matched = matched;
        this.charm = charm;
        this.matchedField = matchedField;
        this.matchedValue = matchedValue;
        this.priority = priority;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public boolean isMatched() {
        return matched;
    }

    public void setMatched(boolean matched) {
        this.matched = matched;
    }

    public CharmData getCharm() {
        return charm;
    }

    public void setCharm(CharmData charm) {
        this.charm = charm;
    }

    public String getMatchedField() {
        return matchedField;
    }

    public void setMatchedField(String matchedField) {
        this.matchedField = matchedField;
    }

    public String getMatchedValue() {
        return matchedValue;
    }

    public void setMatchedValue(String matchedValue) {
        this.matchedValue = matchedValue;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public static class CharmData {

        private String id;
        private String name;
        private String description;
        private String imageUrl;
        private String category;

        public CharmData() {
        }

        public CharmData(
                String id,
                String name,
                String description,
                String imageUrl,
                String category) {

            this.id = id;
            this.name = name;
            this.description = description;
            this.imageUrl = imageUrl;
            this.category = category;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }
    }
}