package com.aws.jug.agentpatterns.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 28 total tools:
 *   - 3 relevant : weather, clothing, currentTime  (the "Amsterdam" scenario)
 *   - 25 dummies : unrelated noise tools
 *
 * All return hardcoded strings so the demo works without real integrations.
 */
public class MyTools {

    // ── Relevant tools ────────────────────────────────────────────────────────

    @Tool(description = "Get the weather for a given location at a given time")
    public String weather(
            String location,
            @ToolParam(description = "ISO date-time string YYYY-MM-DDTHH:mm") String atTime) {
        return "The weather in " + location + " at " + atTime + " is partly cloudy with 28°C and high humidity.";
    }

    @Tool(description = "Get clothing shop names for a given location that are open at a given time")
    public String clothing(
            String location,
            @ToolParam(description = "ISO date-time string YYYY-MM-DDTHH:mm") String openAtTime) {
        return "Clothing shops open in " + location + " at " + openAtTime + ": H&M, Zara, Uniqlo, Mango, Only.";
    }

    @Tool(description = "Get the current date and time for a given location")
    public String currentTime(String location) {
        return "Current date and time in " + location + " is 2025-06-04T15:30:00 IST.";
    }

    // ── Dummy / noise tools (25) ──────────────────────────────────────────────

    @Tool(description = "Check the status of a flight given a flight number")
    public String checkFlightStatus(String flightNumber) {
        return "Flight " + flightNumber + " is on time.";
    }

    @Tool(description = "Book a hotel room at a given city for given check-in and check-out dates")
    public String bookHotel(String city, String checkIn, String checkOut) {
        return "Hotel booked in " + city + " from " + checkIn + " to " + checkOut + ".";
    }

    @Tool(description = "Send a Slack message to a given channel")
    public String sendSlackMessage(String channel, String message) {
        return "Message sent to Slack channel #" + channel + ".";
    }

    @Tool(description = "Look up the latest share price on the stock market by ticker symbol")
    public String getStockPrice(String ticker) {
        return "Stock price for " + ticker + " is $142.30.";
    }

    @Tool(description = "Translate text from a source language to a target language")
    public String translateText(String text, String sourceLang, String targetLang) {
        return "Translated '" + text + "' from " + sourceLang + " to " + targetLang + ".";
    }

    @Tool(description = "Create a new JIRA ticket with a title and description")
    public String createJiraTicket(String title, String description) {
        return "JIRA ticket created: PROJ-1234 - " + title + ".";
    }

    @Tool(description = "Get the top trending news headlines for a given topic")
    public String getNewsHeadlines(String topic) {
        return "Top headlines for '" + topic + "': Headline A, Headline B, Headline C.";
    }

    @Tool(description = "Send an email to a recipient with a subject and body")
    public String sendEmail(String recipient, String subject, String body) {
        return "Email sent to " + recipient + " with subject: " + subject + ".";
    }

    @Tool(description = "Calculate the exchange rate between two currencies")
    public String getExchangeRate(String fromCurrency, String toCurrency) {
        return "1 " + fromCurrency + " = 1.08 " + toCurrency + ".";
    }

    @Tool(description = "Search for restaurants near a given location with optional cuisine filter")
    public String findRestaurants(String location, String cuisine) {
        return "Restaurants near " + location + " serving " + cuisine + ": Resto A, Resto B.";
    }

    @Tool(description = "Report road congestion and driving delays along a route between two places")
    public String getTrafficConditions(String origin, String destination) {
        return "Traffic from " + origin + " to " + destination + " is heavy, 45 min delay.";
    }

    @Tool(description = "Search for available parking spots near a given address")
    public String findParking(String address) {
        return "Available parking near " + address + ": Lot A (5 spots), Lot B (2 spots).";
    }

    @Tool(description = "Look up the live trading value of a cryptocurrency coin by symbol")
    public String getCryptoPrice(String coinSymbol) {
        return "Price of " + coinSymbol + " is $62,400.";
    }

    @Tool(description = "Look up a contact phone number by name in the address book")
    public String lookupContactPhone(String name) {
        return "Phone number for " + name + " is +31 20 123 4567.";
    }

    @Tool(description = "Create a calendar event with a title, date, and time")
    public String createCalendarEvent(String title, String date, String time) {
        return "Calendar event '" + title + "' created on " + date + " at " + time + ".";
    }

    @Tool(description = "Get the menu for a given restaurant")
    public String getRestaurantMenu(String restaurantName) {
        return "Menu for " + restaurantName + ": Starter €8, Main €18, Dessert €7.";
    }

    @Tool(description = "Check if a given domain name is available for registration")
    public String checkDomainAvailability(String domain) {
        return "Domain " + domain + " is available for registration.";
    }

    @Tool(description = "Report the ultraviolet radiation exposure level for sun-safety planning")
    public String getUvIndex(String location) {
        return "UV index in " + location + " is 9 (very high) — sun protection strongly advised.";
    }

    @Tool(description = "Search for job postings by role title and location")
    public String searchJobs(String roleTitle, String location) {
        return "Found 12 " + roleTitle + " jobs in " + location + ".";
    }

    @Tool(description = "Get the latest sports scores for a given sport and team")
    public String getSportsScores(String sport, String team) {
        return team + " latest " + sport + " score: 3-1 win.";
    }

    @Tool(description = "Look up the opening hours of a specific store or business")
    public String getOpeningHours(String businessName) {
        return businessName + " opening hours: Mon-Fri 09:00-18:00, Sat 10:00-17:00.";
    }

    @Tool(description = "Generate a short URL for a given long URL")
    public String shortenUrl(String longUrl) {
        return "Shortened URL: https://short.ly/abc123 (original: " + longUrl + ").";
    }

    @Tool(description = "Get the latest software release version for a given package or library")
    public String getLatestVersion(String packageName) {
        return "Latest version of " + packageName + " is 3.2.1.";
    }

    @Tool(description = "Convert a numeric measurement between Celsius, Fahrenheit and Kelvin units")
    public String convertTemperature(String value, String fromUnit, String toUnit) {
        return value + "°" + fromUnit + " = converted value in °" + toUnit + ".";
    }

    @Tool(description = "Report pollution and air cleanliness readings for a named city")
    public String getAirQualityIndex(String city) {
        return "Air quality index in " + city + " is 156 (Unhealthy) — sensitive groups should limit outdoor exertion.";
    }
}
