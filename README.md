# Concierge App

A smart task management and personal concierge application powered by the Gemini API.

## Features
* **Brain Dump:** Voice-to-task input interface that parses raw voice transcripts into structured tasks, categories, and deadlines.
* **Task Management:** Create, edit, and organize tasks effortlessly.
* **Chat & AI Assistant:** Interact with the intelligent concierge for managing your daily life.
* **Offline Support:** Powered by Firebase with offline persistence.

## Technologies Used
* Kotlin & Jetpack Compose
* Firebase Firestore & Authentication
* Gemini API integration
* Retrofit for networking

## Setup Instructions
1. Clone the repository.
2. Add your `google-services.json` to the `app/` directory (if using Firebase).
3. Ensure your API keys are added in the AI Studio Secrets Panel or via a `.env` file (`GEMINI_API_KEY`).
4. Build and run the application.
