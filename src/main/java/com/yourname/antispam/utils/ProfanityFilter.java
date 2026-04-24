package com.yourname.antispam.utils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility class for filtering profanity and blocked words from messages.
 * Includes normalization to prevent bypassing with spaces, symbols, etc.
 */
public class ProfanityFilter {
    private final Set<String> blockedWords;
    
    public ProfanityFilter(List<String> blockedWordsList) {
        this.blockedWords = new HashSet<>();
        if (blockedWordsList != null) {
            for (String word : blockedWordsList) {
                if (word != null && !word.trim().isEmpty()) {
                    // Store normalized version for matching
                    this.blockedWords.add(normalizeString(word));
                }
            }
        }
    }
    
    /**
     * Normalize a string by removing spaces, symbols, and special characters.
     * Keeps only letters, numbers, and converts to lowercase.
     * This prevents bypassing filters with spaces/symbols (e.g., "傻 逼", "傻@逼").
     * 
     * @param str The string to normalize
     * @return Normalized string with only letters and numbers
     */
    private String normalizeString(String str) {
        if (str == null || str.isEmpty()) {
            return "";
        }
        
        // Remove all non-letter and non-digit characters, convert to lowercase
        return str.toLowerCase()
                  .replaceAll("[^a-z0-9\\p{L}\\p{N}]", "");
    }
    
    /**
     * Check if a message contains any blocked words.
     * Uses normalization to prevent bypassing with spaces/symbols.
     * 
     * @param message The message to check
     * @return true if the message contains blocked words
     */
    public boolean containsProfanity(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        
        // Normalize the message to prevent bypassing
        String normalizedMessage = normalizeString(message);
        
        // Check each blocked word
        for (String blockedWord : blockedWords) {
            if (normalizedMessage.contains(blockedWord)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Find the first blocked word in a message.
     * Uses normalization to prevent bypassing with spaces/symbols.
     * 
     * @param message The message to check
     * @return The first blocked word found, or null if none found
     */
    public String findBlockedWord(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }
        
        // Normalize the message to prevent bypassing
        String normalizedMessage = normalizeString(message);
        
        for (String blockedWord : blockedWords) {
            if (normalizedMessage.contains(blockedWord)) {
                return blockedWord;
            }
        }
        
        return null;
    }
    
    /**
     * Get the number of blocked words in the filter.
     * 
     * @return The count of blocked words
     */
    public int getBlockedWordCount() {
        return blockedWords.size();
    }
    
    /**
     * Update the blocked words list.
     * 
     * @param blockedWordsList New list of blocked words
     */
    public void updateBlockedWords(List<String> blockedWordsList) {
        blockedWords.clear();
        if (blockedWordsList != null) {
            for (String word : blockedWordsList) {
                if (word != null && !word.trim().isEmpty()) {
                    // Store normalized version
                    blockedWords.add(normalizeString(word));
                }
            }
        }
    }
}
