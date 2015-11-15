package com.codeandunicorns.face.doyousmile;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.codeandunicorns.face.doyousmile.uicamera.CameraSourcePreview;
import com.codeandunicorns.face.doyousmile.uicamera.GraphicOverlay;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import java.io.IOException;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "FaceTracker";

    private CameraSource mCameraSource;
    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;
    private ImageView mImageView;
    private TextView mJokeView;
    private TextToSpeech mTts;
    private static final int CAMERA_REQUEST = 1888;
    private float smileValue = 1.0f;
    private String[] jokes;
    private String[] quotes;

    //==============================================================================================
    // Activity Methods
    //==============================================================================================

    /**
     * Initializes the UI and initiates the creation of a face detector. It also initializes strings for quotes and inspiration
     */
    //@Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_main);

        AdView mAdView = (AdView) findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);

        mPreview = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);
        mImageView = (ImageView) findViewById(R.id.imageView);
        mJokeView = (TextView) findViewById(R.id.jokeView);
        mImageView.setVisibility(View.INVISIBLE);

        Context context = getApplicationContext();
        FaceDetector detector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .setProminentFaceOnly(true)
                .build();
        detector.setProcessor(
                new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory()).build());


        if (!detector.isOperational()) {
            // Note: The first time that an app using face API is installed on a device, GMS will
            // download a native library to the device in order to do detection.  Usually this
            // completes before the app is run for the first time.  But if that download has not yet
            // completed, then the above call will not detect any faces.
            //
            // isOperational() can be used to check if the required native library is currently
            // available.  The detector will automatically become operational once the library
            // download completes on device.
            Log.w(TAG, "Face detector dependencies are not yet available.");
        }

        mCameraSource = new CameraSource.Builder(context, detector)
                .setRequestedPreviewSize(640, 480)
                .setFacing(CameraSource.CAMERA_FACING_FRONT)
                .setRequestedFps(30.0f)
                .build();
        mTts = new TextToSpeech(MainActivity.this, new TextToSpeech.OnInitListener(){
            @Override
            public void onInit(int status) {
                // TODO Auto-generated method stub
                if(status == TextToSpeech.SUCCESS){
                    int result=mTts.setLanguage(Locale.US);
                    if(result==TextToSpeech.LANG_MISSING_DATA ||
                            result==TextToSpeech.LANG_NOT_SUPPORTED){
                        Log.e("error", "This Language is not supported");
                    }
                    else{
                        if (android.os.Build.VERSION.SDK_INT>=21){
                            //mTts.speak("Welcome to: Do you smile?", TextToSpeech.QUEUE_FLUSH, null, "s1");
                            //mTts.speak("Scan your current mood! Do you smile or not is the question?\nClick and find out!", TextToSpeech.QUEUE_FLUSH, null, "s2");
                        }
                        else{
                            //mTts.speak("Welcome to: Do you smile?", TextToSpeech.QUEUE_FLUSH, null);
                            //mTts.speak("Scan your current mood! Do you smile or not is the question?\nClick and find out!", TextToSpeech.QUEUE_FLUSH, null);
                        }
                    }
                }
                else
                    Log.e("error", "Initilization Failed!");
            }
        });

        Button button =  (Button) findViewById(R.id.takepicturebutton); //take mood picture
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mImageView.setVisibility(View.INVISIBLE);
                mPreview.takeImage();
            }
        });

        initJokeStrings();
        initInspiriStrings();

    }

    /**
     * Restarts the camera.
     */
    @Override
    protected void onResume() {
        super.onResume();
        startCameraSource();
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        mPreview.stop();
    }

    /**
     * Releases the resources associated with the camera source, the associated detector, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCameraSource.release();
    }

    //==============================================================================================
    // Camera Source Preview
    //==============================================================================================

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {
        try {
            mPreview.start(mCameraSource, mGraphicOverlay);
        } catch (IOException e) {
            Log.e(TAG, "Unable to start camera source.", e);
            mCameraSource.release();
            mCameraSource = null;
        }
    }

    //==============================================================================================
    // Graphic Face Tracker
    //==============================================================================================

    /**
     * Factory for creating a face tracker to be associated with a new face.  The multiprocessor
     * uses this factory to create face trackers as needed -- one for each individual.
     */
    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new GraphicFaceTracker(mGraphicOverlay);
        }
    }

    public void setImageViewPicture(final byte[] data){
        mImageView.setVisibility(View.VISIBLE);
        mImageView.setImageBitmap(BitmapFactory.decodeByteArray(data, 0, data.length));
        if(smileValue >= 0.45f){
            getInspirationalQoute(); // You are smiling
        }
        else{
            getRandomJoke(); //You do not smile
        }
    }

    /**
     * Face tracker for each detected individual. This maintains a face graphic within the app's
     * associated face overlay.
     */
    private class GraphicFaceTracker extends Tracker<Face> {
        private GraphicOverlay mOverlay;
        private FaceGraphic mFaceGraphic;

        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
            mFaceGraphic = new FaceGraphic(overlay);
        }

        /**
         * Start tracking the detected face instance within the face overlay.
         */
        @Override
        public void onNewItem(int faceId, Face item) {
            mFaceGraphic.setId(faceId);
        }

        /**
         * Update the position/characteristics of the face within the overlay.
         */
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            mOverlay.add(mFaceGraphic);
            mFaceGraphic.updateFace(face);
            smileValue = mFaceGraphic.getSmileProbability();
        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            mOverlay.remove(mFaceGraphic);
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone() {
            mOverlay.remove(mFaceGraphic);
        }
    }

    public void getRandomJoke(){
        if (android.os.Build.VERSION.SDK_INT>=21){
            String joke = jokes[new Random().nextInt(jokes.length)];
            mTts.speak(joke, TextToSpeech.QUEUE_FLUSH, null, "ss");
            mJokeView.setText("Joke, cause you don't smile:" + "\n" + joke);
        }
        else{
            mTts.speak(jokes[new Random().nextInt(jokes.length)], TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    public void getInspirationalQoute() {
        if (android.os.Build.VERSION.SDK_INT>=21){
            String quote = quotes[new Random().nextInt(quotes.length)];
            mTts.speak(quote, TextToSpeech.QUEUE_FLUSH, null, "ss");
            mJokeView.setText("Quote, cause you smile:" + "\n" + quote);
        }
        else{
            mTts.speak(quotes[new Random().nextInt(quotes.length)], TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    public void initJokeStrings(){
        jokes = new String[] {
                "The clear history button in your browser has saved more lives than Superman",
                "I bet earth makes fun of the other planets for having no life",
                "Oh no! Playstation and Xbox online services are down! Someone call an ambulance! Wii U Wii U Wii U.",
                "The sight of a woman's cleavage reduces a man's ability to think clearly by 50%... per boob!",
                "Jimmy has 42 candy bars. Jimmy eats 24. What does jimmy have now? Diabetes.. Jimmy has diabetes.",
                "What type of Bee's make milk instead of honey? Boobies",
                "I gave my number to a really hot girl at the bar and told her to text me when she got home. She must be homeless",
                "Just changed my Facebook name to ‘No one' so when I see stupid posts I can click like and it will say ‘No one likes this'.",
                "What's the difference between snowmen and snowladies? Snowballs",
                "How do you make holy water? You boil the hell out of it.",
                "If con is the opposite of pro, it must mean Congress is the opposite of progress?",
                "I wondered why the frisbee was getting bigger, and then it hit me.",
                "I used to like my neighbors, until they put a password on their Wi-Fi.",
                "Never argue with a fool, they will lower you to their level, and then beat you with experience.",
                "Why do farts smell? So deaf people can enjoy them too.",
                "Light travels faster than sound. This is why some people appear bright until they speak.",
                "What did the ocean say to the beach? Nothing, it just waved.",
                "I say no to alcohol, it just doesn't listen.",
                "Why did the skeleton go to the party alone? -- He had no body to go with him!",
                "Right now I'm having amnesia and deja vu at the same time! I think I've forgotten this before?",
                "Why is Peter Pan always flying? Because he Neverlands.",
                "I think i want a job cleaning mirrors, It's just something i could really see myself doing",
                "My girlfriend keeps going on bout the time i jokingly put superglue on her mobile phone, honestly , she just can't let it go.",
                "How do you make a tissue dance? You put a little boogie in it.",
                "For Christmas, I want Santa's list of naughty girls.",
                "Who says nothing is impossible. I've been doing nothing for years."
        };
    }

    public void initInspiriStrings(){
        quotes = new String[] {
                "Things may come to those who wait, but only the things left by those who hustle. By Abraham Lincoln",
                "The great secret of education is to direct vanity to proper objects. By Adam Smith",
                "It’s not that travel just broadens your mind, rather it enables you to see how narrow your oppressor’s minds are. By Alain de Botton",
                "A person who never made a mistake never tried anything new. By Alan Watts",
                "Better to have a short life that is full of what you like doing than a long life spent in a miserable way. By Alan Watts",
                "Life is a musical thing and you are supposed to dance and sign while it's being played. By Alan Watts",
                "Omnipotence is not knowing how everything is done; it's just doing it. By Alan Watts",
                "It is not humiliating to be unhappy. Physical suffering is sometimes humiliating, but the suffering of being cannot be, it is life. By Albert Camus",
                "Learn from yesterday, live for today, hope for tomorrow. The important thing is not to stop questioning. By Albert Einstein",
                "The secret to creativity is knowing how to hide your sources. By Albert Einstein",
                "The true sign of intelligence is not knowledge but imagination. By Albert Einstein",
                "The world as we have created it is a process of our thinking. It cannot be changed without changing our thinking. By Albert Einstein",
                "When facts don't fit the theory, change the facts. By Albert Einstein",
                "Creativity never comes under emotional stress or tension. The real creativity comes when the mind finally relaxes and is quiet and then can focus. By Amar Bose",
                "If you think something is impossible, don't disturb the person doing it. By Amar Bose",
                "If you want to build a ship, don't drum up people to collect wood and don't assign them tasks and work, but rather teach them to long for the endless immensity of the sea. By Antoine de Saint-Exupery",
                "To love is not to look at one another: it is to look, together, in the same direction. By Antoine de Saint-Exupery",
                "When you give yourself, you receive more than you give. By Antoine de Saint-Exupery",
                "Do not fear to be eccentric in opinion, for every opinion now accepted was once eccentric. By Bertrand Russell",
                "Visionaries not only believe that the impossible can be done, but that it must be done. By Bran Ferren",
                "People who have a strong sense of love and belonging believe they're worthy of it. By Brene Brown",
                "Every human being has some flavor of ‘not enough.’ You can either be stopped by it, or simply notice it, like the weather. By Brigid Schulte",
                "Anyone can complain about the world, but only a good few can fix it. By Cennydd Bowles",
                "So many people rush through life, let's take our time living it. By Christian Vuerings",
                "The more that you read, the more things you will know. The more that you learn, the more places you’ll go. By Doctor Seuss",
                "Dichotomy between sense of wonder and what is wrong. By Elon Musk",
                "If something is important enough, you should try. Even if the probable outcome is failure. By Elon Musk",
                "There's skepticism about anything new. That's normal. By Elon Musk",
                "There is only one way to happiness and that is to cease worrying about things which are beyond the power of our will. By Epictetus",
                "There are 2 ways to make a man richer, give him more money or curb his desires. By Jean-Jacques Rousseau",
                "Being deeply loved by someone gives you strength, while loving someone deeply gives you courage. By Lao Tse",
                "A leader is best when people barely know he exists, when his work is done, his aim fulfilled, they will say: we did it ourselves. By Lao Tse",
                "Silence is a source of great strength. By Lao Tse",
                "To attain knowledge, add things every day. To attain wisdom, remove things every day. By Lao Tse",
                "The best way to have a good idea is to have lots of ideas. By Linus Pauling",
                "At home I am a nice guy: but I don't want the world to know. Humble people, I've found, don't get very far. By Muhammad Ali",
                "He who is not courageous enough to take risks will accomplish nothing in life. By Muhammad Ali",
                "I am the greatest, I said that even before I knew I was. By Muhammad Ali",
                "It isn't the mountains ahead to climb that wear you out; it's the pebble in your shoe. By Muhammad Ali",
                "The man who has no imagination has no wings. By Muhammad Ali",
                "If there is no struggle, there is no progress. By Frederick Douglass",
                "Success is a state of being. Because as soon as you say you're successful, you probably start to fail. By Howard H. Stevenson",
                "The reasonable man adapts himself to the world; the unreasonable one persists in trying to adapt the world to himself. Therefore, all progress depends on the unreasonable man. By George Bernard Shaw",
                "Life would be much easier to understand if mother nature gave us the source code. By Graeme MacWilliam",
                "Coming together is a beginning; keeping together is progress; staying together is success. By Henry Ford",
                "An ounce of experience is better than a ton of theory simply. By John Dewey",
                "A journey of a thousand miles begins with a single step. By Laozi",
                "A man is but the product of his thoughts what he thinks, he becomes. By Mahatma Gandhi",
                "An ounce of practice is worth more than tons of preaching. By Mahatma Gandhi",
                "Live as if you were to die tomorrow. Learn as if you were to live forever. By Mahatma Gandhi",
                "You must be the change you wish to see in the world. By Mahatma Gandhi",
                "You must be the change you wish to see in the world. By Mahatma Gandhi",
                "I have never let my schooling interfere with my education. By Mark Twain",
                "Never let the future disturb you. You will meet it, if you have to, with the same weapons of reason which today arm you against the present. By ",
                "Never believe that a few caring people can't change the world. For, indeed, that's all who ever have. By ",
                "Keep away from those who try to belittle your ambitions. Small people always do that, but the really great make you believe that you too can become great. By ",
                "Life is short, break the rules. By ",
                "A gentleman is one who never hurts anyone's feelings unintentionally. By Oscar Wilde",
                "A man who does not think for himself does not think at all. By Oscar Wilde",
                "Always forgive your enemies - nothing annoys them so much. By Oscar Wilde",
                "America is the only country that went from barbarism to decadence without civilization in between. By Oscar Wilde",
                "Between men and women there is no friendship possible. There is passion, enmity, worship, love, but no friendship. By Oscar Wilde",
                "Education is an admirable thing, but it is well to remember from time to time that nothing that is worth knowing can be taught. By Oscar Wilde",
                "Experience is simply the name we give our mistakes. By Oscar Wilde",
                "If you want to tell people the truth, make them laugh, otherwise they’ll kill you. By Oscar Wilde",
                "There are only two tragedies in life: one is not getting what one wants, and the other is getting it. By Oscar Wilde",
                "Work is the curse of the drinking classes. By Oscar Wilde",
                "If you’re far enough ahead that people can’t figure out if you’re joking, you know you’ve innovated. By Paul Buchheit",
                "The first thing I do on day one is build something useful, then just keep improving it. By Paul Buchheit",
                "Go where you’re celebrated, not where you’re tolerated. By Paul F. Davis",
                "Maintain your spirit of curiosity, keep questioning things, and you’ll find new ways to innovate. By Richard Brandson",
                "If I look confused it is because I am thinking. By Samuel Goldwyn",
                "The harder I work, the luckier I get. By Samuel Goldwyn",
                "When someone does a good job, applaud; it makes two people happy. By Samuel Goldwyn",
                "Defenseless is the best choice for those seeking to grow. By Seth Godin",
                "Ship often. Ship lousy stuff, but ship. Ship constantly. By Seth Godin",
                "If you do something and it turns out pretty good, then you should go do something else wonderful, not dwell on it for too long. Just figure out what’s next. By Steve Jobs",
                "Do not go where the path may lead, go instead where there is no path and leave a trail. By Ralph Waldo Emerson",
                "For every minute you remain angry, you give up sixty seconds of peace of mind. By Ralph Waldo Emerson",
                "Nothing great was ever achieved without enthusiasm. By Ralph Waldo Emerson",
                "If you’re not a embarrassed by the first version of your product, you launched to late. By Reid Hoffman",
                "You earn trust by providing innovative, quality products and keeping your word. By Richard Brandson",
                "Nothing comes to a sleeper but a dream. By Richard Williams",
                "While we teach, we learn. By Seneca The Younger",
                "Being the richest man in the cemetery doesn’t matter to me … Going to bed at night saying we’ve done something wonderful… that’s what matters to me. By Steve Jobs",
                "One must learn by doing the thing; for though you think you know it, you have no certainty until you try. By Sophocles",
                "The enemy of sustainable productivity is not stress. Rather, it’s the absence of intermittent rest and renewal. By Tony Schwartz",
                "I keep going anyway. I pause and take the doubts in. I cry. I curse. I think it's unfair and that I can't continue. But then I do. I get up, brush my shoulders off, and carry on. By Tiffany Han",
                "A man is not idle because he is absorbed in thought. There is a visible labor and there is an invisible labor. By Victor Hugo",
                "He who opens a school door, closes a prison. By Victor Hugo",
                "Initiative is doing the right thing without being told. By Victor Hugo",
                "There is nothing more powerful than an idea whose time has come. By Victor Hugo",
                "When a woman is talking to you, listen to what she says with her eyes. By Victor Hugo",
                "Being male is a matter of birth. Being a man is a matter of age. But being a Gentleman is a matter of choice. By Vin Diesel",
                "Education shouldn't fill a bucket but light a fire. By William Butler Yeats",
                "There are no strangers here; Only friends you haven't yet met. By William Butler Yeats",
        };
    }

}
