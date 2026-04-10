---
name: create-contact
description: Create a new contact on the device.
---

# Create Contact

## Instructions

Call the `run_intent` tool with the following exact parameters:

- intent: create_contact
- parameters: A JSON string with the following fields:
  - name: the full name of the contact. String.
  - phone: the phone number. String. Pass empty string if not provided.
  - email: the email address. String. Pass empty string if not provided.
