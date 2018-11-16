import com.messagebird.MessageBirdClient;
import com.messagebird.MessageBirdService;
import com.messagebird.MessageBirdServiceImpl;
import com.messagebird.exceptions.GeneralException;
import com.messagebird.exceptions.NotFoundException;
import com.messagebird.exceptions.UnauthorizedException;
import com.messagebird.objects.Lookup;
import com.messagebird.objects.MessageResponse;
import com.messagebird.objects.Verify;
import io.github.cdimascio.dotenv.Dotenv;
import spark.ModelAndView;
import spark.template.mustache.MustacheTemplateEngine;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static spark.Spark.get;
import static spark.Spark.post;


public class AppointmentReminders {
    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.load();

        // Create a MessageBirdService
        final MessageBirdService messageBirdService = new MessageBirdServiceImpl(dotenv.get("MESSAGEBIRD_API_KEY"));
        // Add the service to the client
        final MessageBirdClient messageBirdClient = new MessageBirdClient(messageBirdService);

        List<Map<String, Object>> appointmentDb = new ArrayList<Map<String, Object>>();

        get("/",
                (req, res) ->
                {
                    LocalDateTime today = LocalDateTime.now();

                    // On the form, we're showing a default appointment
                    // time 3:10 hours from now to simplify testing input
                    LocalDateTime futureTime = today.plusHours(3).plusMinutes(10);

                    Map<String, Object> model = new HashMap<>();
                    model.put("date", futureTime.toLocalDate());

                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
                    model.put("time", formatter.format(futureTime.toLocalTime()));

                    return new ModelAndView(model, "home.mustache");
                },
                new MustacheTemplateEngine()
        );

        post("/book",
                (req, res) ->
                {
                    String name = req.queryParams("name");
                    String treatment = req.queryParams("treatment");
                    String number = req.queryParams("number");
                    String date = req.queryParams("date");
                    String time = req.queryParams("time");

                    Map<String, Object> model = new HashMap<>();
                    model.put("name", name);
                    model.put("treatment", treatment);
                    model.put("number", number);
                    model.put("date", date);
                    model.put("time", time);

                    // Check if user has provided input for all form fields
                    if (name.isBlank() || treatment.isBlank() || number.isBlank() || date.isBlank()  || time.isBlank()) {
                        model.put("errors", "Please fill all required fields!");
                        return new ModelAndView(model, "home.mustache");
                    }

                    // Check if date/time is correct and at least 3:05 hours in the future
                    LocalDateTime earliestPossibleDt =  LocalDateTime.now().plusHours(3).plusMinutes(5);
                    LocalDateTime appointmentDt =  LocalDateTime.parse(String.format("%sT%s", date, time));

                    if (appointmentDt.isBefore(earliestPossibleDt)) {
                        model.put("errors", "You can only book appointments that are at least 3 hours in the future!");
                        return new ModelAndView(model, "home.mustache");
                    }

                    try {
                        // convert String number into acceptable format
                        BigInteger phoneNumber = new BigInteger(number);
                        final Lookup lookupRequest = new Lookup(phoneNumber);
                        lookupRequest.setCountryCode(dotenv.get("COUNTRY_CODE"));
                        final Lookup lookup = messageBirdClient.viewLookup(lookupRequest);

                        if (lookup.getType() != "mobile") {
                            model.put("errors", "You have entered a valid phone number, but it's not a mobile number! Provide a mobile number so we can contact you via SMS.");
                            return new ModelAndView(model, "home.mustache");
                        }

                        // Schedule reminder 3 hours prior to the treatment
                        LocalDateTime reminderDt = appointmentDt.minusHours(3);

                        String body = String.format("%s, here's a reminder that you have a %s scheduled for %s. See you soon!", name, treatment, DateTimeFormatter.ofPattern("HH:mm").format(appointmentDt));

                        final List<BigInteger> phones = new ArrayList<BigInteger>();
                        phones.add(phoneNumber);
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
                    } catch (UnauthorizedException | GeneralException | NotFoundException ex) {
                        model.put("errors", ex.toString());
                        return new ModelAndView(model, "home.mustache");
                    }
                },
                new MustacheTemplateEngine()
        );


        post("/step3",
                (req, res) ->
                {
                    String id = req.queryParams("id");
                    String token = req.queryParams("token");

                    Map<String, Object> model = new HashMap<>();

                    try {
                        final Verify verify = messageBirdClient.verifyToken(id, token);

                        return new ModelAndView(model, "step3.mustache");
                    } catch (UnauthorizedException | GeneralException ex) {
                        model.put("errors", ex.toString());
                        return new ModelAndView(model, "step2.mustache");
                    }
                },
                new MustacheTemplateEngine()
        );
    }
}