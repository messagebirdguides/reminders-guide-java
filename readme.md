# SMS Appointment Reminders

### ⏱ 15 min build time

## Why build SMS appointment reminders?

Booking appointments online from a website or mobile app is quick and easy. Customers just have to select their desired date and time, enter their personal details and hit a button. The problem, however, is that easy-to-book appointments are often just as easy to forget.

For appointment-based services, no-shows are annoying and costly because of the time and revenue lost waiting for a customer instead of serving them, or another customer. Timely SMS reminders act as a simple and discrete nudges, which can go a long way in the prevention of costly no-shows.

## Getting Started

In this MessageBird Developer Guide, we'll show you how to use the MessageBird SMS messaging API to build an SMS appointment reminder application in Ruby.

This sample application represents the order website of a fictitious online beauty salon called _BeautyBird_. To reduce the growing number of no-shows, BeautyBird now collects appointment bookings through a form on their website and schedules timely SMS reminders to be sent out three hours before the selected date and time.

To look at the full sample application or run it on your computer, go to the [MessageBird Developer Guides GitHub repository](https://github.com/messagebirdguides/reminders-guide-java) and clone it or download the source code as a ZIP archive. You will need [Java 1.8](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) and [Maven](https://maven.apache.org/) to run the example.

The `pom.xml` file has all the dependencies the project needs. Your IDE should be configured to automatically install them.


## Configuring the MessageBird SDK

The SDK is loaded with the following lines at the beginning of the application:

``` java
import com.messagebird.MessageBirdClient;
import com.messagebird.MessageBirdService;
import com.messagebird.MessageBirdServiceImpl;

import io.github.cdimascio.dotenv.Dotenv;

Dotenv dotenv = Dotenv.load();

// Create a MessageBirdService
final MessageBirdService messageBirdService = new MessageBirdServiceImpl(dotenv.get("MESSAGEBIRD_API_KEY"));

// Add the service to the client
final MessageBirdClient messageBirdClient = new MessageBirdClient(messageBirdService);
```

The MessageBird API key needs to be provided as a parameter.

**Pro-tip:** Hardcoding your credentials is a risky practice that should never be used in production applications. A better method, also recommended by the [Twelve-Factor App Definition](https://12factor.net/), is to use environment variables.

We've added [dotenv](https://mvnrepository.com/artifact/io.github.cdimascio/java-dotenv) to the sample application, so you can supply your API key in a file named `.env`. You can copy the provided file `env.example` to `.env` and add your API key like this:

```
MESSAGEBIRD_API_KEY=YOUR-API-KEY
```

API keys can be created or retrieved from the API access (REST) tab in the Developers section of your MessageBird account.

## Collecting User Input

In order to send SMS messages to users, you need to collect their phone number as part of the booking process. We have created a sample form that asks the user for their name, desired treatment, number, date and time. For HTML forms it's recommended to use `type="tel"` for the phone number input. You can see the template for the complete form in the file `views/home.mustache` and the route that drives it is defined as `get("/")` in the application.

## Storing Appointments & Scheduling Reminders

The user's input is sent to the route `post("/book")` defined in the application. The implementation covers the following steps:

### Step 1: Check their input

Validate that the user has entered a value for every field in the form.

### Step 2: Check the appointment date and time

Confirm that the date and time are valid and at least three hours and five minutes in the future. BeautyBird won't take bookings on shorter notice. Also, since we want to schedule reminders three hours before the treatment, anything else doesn't make sense from a testing perspective. 

``` java
// Check if date/time is correct and at least 3:05 hours in the future
LocalDateTime earliestPossibleDt =  LocalDateTime.now().plusHours(3).plusMinutes(5);
LocalDateTime appointmentDt =  LocalDateTime.parse(String.format("%sT%s", date, time));

```

## Step 3: Check their phone number

Check whether the phone number is correct. This can be done with the [MessageBird Lookup API](https://developers.messagebird.com/docs/lookup#lookup-request), which takes a phone number entered by a user, validates the format and returns information about the number, such as whether it is a mobile or fixed line number. This API doesn't enforce a specific format for the number but rather understands a variety of different variants for writing a phone number, for example using different separator characters between digits, giving your users the flexibility to enter their number in various ways. In the SDK, you can call `client.viewLookup`:

``` java
// convert String number into acceptable format
BigInteger phoneNumber = new BigInteger(number);
final Lookup lookupRequest = new Lookup(phoneNumber);
lookupRequest.setCountryCode(dotenv.get("COUNTRY_CODE"));
final Lookup lookup = messageBirdClient.viewLookup(lookupRequest);
```

The function takes a single argument of type `Lookup`. When constructing `Lookup`, you must provide the phone number as a `BigInteger`, You can optionally provide a country code by setting it via `setCountryCode`. 

To assign a country code, add the following line to you `.env` file, replacing NL with your own ISO country code:

```
COUNTRY_CODE=NL
```

In the `lookup` response, we handle several different cases:

* An error (code 21) occurred, which means MessageBird was unable to parse the phone number.
* Another error code occurred, which means something else went wrong in the API.
* No error occurred, but the value of the response's type attribute is something other than mobile.
* Everything is OK, which means a mobile number was provided successfully.

We can capture and report all of these issues with a single `try...catch` block, and report the errors back as a string:

``` java
try {
        final Lookup lookup = messageBirdClient.viewLookup(lookupRequest);
    } catch (UnauthorizedException | GeneralException | NotFoundException ex) {
        model.put("errors", ex.toString());
        return new ModelAndView(model, "home.mustache");
    }
```

## Step 4: Schedule the reminder

Using `LocalDateTime`, we can easily specify the time for our reminder:

``` java
// Schedule reminder 3 hours prior to the treatment
LocalDateTime reminderDt = appointmentDt.minusHours(3);
```

Then it's time to call MessageBird's API:

``` java
String body = String.format("%s, here's a reminder that you have a %s scheduled for %s. See you soon!", name, treatment, DateTimeFormatter.ofPattern("HH:mm").format(appointmentDt));

final List<BigInteger> phones = new ArrayList<BigInteger>();
phones.add(phoneNumber);
final MessageResponse response = messageBirdClient.sendMessage("BeautyBird", body, phones);

```

Let's break down the parameters that are set with this call of `client.sendMessage`:

* `originator`: This is the first parameter. It represents the sender ID. You can use a mobile number here, or an alphanumeric ID, like in the example.
* `body`: This is the second parameter. It's the friendly text for the message.
* `recipients`: This is the third parameter. It's an array of phone numbers. We just need one number, and we're using the normalized number returned from the Lookup API instead of the user-provided input.


## Step 5: Store the appointment

We're almost done! The application's logic continues with the `response`, where we need to handle both success and error cases:

``` java
final MessageResponse response = messageBirdClient.sendMessage("BeautyBird", body, phones);

Map<String, Object> appointment = new HashMap<>();
appointment.put("name", name);
appointment.put("treatment", treatment);
appointment.put("number", number);
appointment.put("appointmentDt", appointmentDt);
appointment.put("reminderDt", reminderDt);

appointmentDb.add(appointment);

// Render confirmation page
return new ModelAndView(appointment, "confirm.mustache");
```

As you can see, for the purpose of the sample application, we simply "persist" the appointment to a global variable in memory (`appointmentDb`). This is where, in practical applications, you would write the appointment to a persistence layer such as a file or database. We also show a confirmation page, which is defined in `confirm.mustache`.

## Testing the Application

Build and run the application through your IDE.


Then, point your browser at `localhost:4567` to see the form and schedule your appointment! If you've used a live API key, a message will arrive to your phone three hours before the appointment! But don't actually leave the house, this is just a demo :)

## Nice work!

You now have a running SMS appointment reminder application!

You can now use the flow, code snippets and UI examples from this tutorial as an inspiration to build your own SMS reminder system. Don't forget to download the code from the [MessageBird Developer Guides GitHub repository](https://github.com/messagebirdguides/reminders-guide-ruby).

## Next steps

Want to build something similar but not quite sure how to get started? Please feel free to let us know at support@messagebird.com, we'd love to help!
