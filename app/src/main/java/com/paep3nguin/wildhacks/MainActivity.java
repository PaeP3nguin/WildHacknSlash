package com.paep3nguin.wildhacks;

import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.devpaul.bluetoothutillib.SimpleBluetooth;
import com.devpaul.bluetoothutillib.utils.SimpleBluetoothListener;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity implements SimpleBluetoothListener, SensorEventListener {

    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;

    /* Game parameters */
    private static final int BLOCK_SENSITIVITY = 10;
    private static final int ATTACK_SENSITIVITY = 10;
    private static final int ACTION_DELAY = 400;
    private static final int REACTION_TIME = 400;
    private static final int MAX_HEALTH = 10;

    /* Bluetooth constants */
    private static final int YOU_WIN = 1;
    private static final int PLAY_AGAIN = 2;
    private static final int HACK_VERTICAL = 10;
    private static final int HACK_LEFT = 20;
    private static final int HACK_RIGHT = 30;
    private static final int STAB = 40;
    private static final String SERVER_MAC = "F8:E0:79:43:B3:B0";
    private static final String OLD_MAC = "F8:E0:79:6E:EB:68";

    /* Sound IDs */
    private static final int ATTACK1 = 0;
    private static final int ATTACK2 = 1;
    private static final int BLOCK1 = 2;
    private static final int BLOCK4 = 5;
    private static final int HIT_SOUND = 6;
    private static final int LOSS_SOUND = 7;
    private static final int WIN_SOUND = 8;
    private static final int SWING_1 = 9;
    private static final int SWING_5 = 13;
    private static final int HIT_1 = 14;
    private static final int HIT_7 = 20;

    private Dialog dialog;
    View whiteBar;
    View redBar;

    @Bind(R.id.toolbar) Toolbar toolbar;

    private SimpleBluetooth simpleBluetooth;
    private Handler handler;
    private Vibrator v;
    private SoundPool soundPool;
    private ArrayList<Integer> soundIDs;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;

    private LinkedList<Float[]> lastAccels;
    boolean[] lastBig = new boolean[]{false, false, false};
    boolean[] lastSmall = new boolean[]{false, false, false};
    private long lastActionTime = 0;
    private Random random;
    private BluetoothDevice bluetoothDevice;

    /* Game status */
    private int health = MAX_HEALTH;
    private AtomicInteger action;
    private boolean in_game = false;
    private boolean star_wars = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);

        dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog);
        dialog.setCancelable(false);
        whiteBar = ButterKnife.findById(dialog, R.id.red_bar);
        redBar = ButterKnife.findById(dialog, R.id.white_bar);

        setSupportActionBar(toolbar);

        handler = new Handler();
        v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        soundPool = new SoundPool(2, AudioManager.STREAM_MUSIC, 0);
        soundIDs = new ArrayList<>();
        soundIDs.add(soundPool.load(this, R.raw.knife_stab, 1));
        soundIDs.add(soundPool.load(this, R.raw.swipe, 1));
        soundIDs.add(soundPool.load(this, R.raw.swords_hit_1, 1));
        soundIDs.add(soundPool.load(this, R.raw.swords_hit_2, 1));
        soundIDs.add(soundPool.load(this, R.raw.swords_hit_3, 1));
        soundIDs.add(soundPool.load(this, R.raw.swords_hit_4, 1));
        soundIDs.add(soundPool.load(this, R.raw.wilhelm, 1));
        soundIDs.add(soundPool.load(this, R.raw.fatality, 1));
        soundIDs.add(soundPool.load(this, R.raw.triumph, 1));
        soundIDs.add(soundPool.load(this, R.raw.swing_1, 1));
        soundIDs.add(soundPool.load(this, R.raw.swing_2, 1));
        soundIDs.add(soundPool.load(this, R.raw.swing_3, 1));
        soundIDs.add(soundPool.load(this, R.raw.swing_4, 1));
        soundIDs.add(soundPool.load(this, R.raw.swing_5, 1));
        soundIDs.add(soundPool.load(this, R.raw.hit_1, 1));
        soundIDs.add(soundPool.load(this, R.raw.hit_2, 1));
        soundIDs.add(soundPool.load(this, R.raw.hit_3, 1));
        soundIDs.add(soundPool.load(this, R.raw.hit_4, 1));
        soundIDs.add(soundPool.load(this, R.raw.hit_5, 1));
        soundIDs.add(soundPool.load(this, R.raw.hit_6, 1));
        soundIDs.add(soundPool.load(this, R.raw.hit_7, 1));

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        simpleBluetooth = new SimpleBluetooth(this, this);
        simpleBluetooth.initializeSimpleBluetooth();
        simpleBluetooth.setSimpleBluetoothListener(this);

        lastAccels = new LinkedList<>();

        action = new AtomicInteger(0);
        random = new Random();
    }

    @Override
    protected void onResume() {
        if (in_game) {
            mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
        }

        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        simpleBluetooth.endSimpleBluetooth();

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @OnClick(R.id.throw_down)
    public void throwDown() {
        if (bluetoothDevice != null) {
            startGame();
            simpleBluetooth.sendData(PLAY_AGAIN);
            return;
        }

        simpleBluetooth.initializeSimpleBluetooth();
        simpleBluetooth.createBluetoothServerConnection();
    }

    @OnClick(R.id.fight_button)
    public void fight() {
        if (bluetoothDevice != null) {
            startGame();
            simpleBluetooth.sendData(PLAY_AGAIN);
            return;
        }

        simpleBluetooth.initializeSimpleBluetooth();
        Log.i("WildHacks", "My MAC is: " + BluetoothAdapter.getDefaultAdapter().getAddress());
        if (BluetoothAdapter.getDefaultAdapter().getAddress().equals(OLD_MAC)) {
            simpleBluetooth.connectToBluetoothServer(SERVER_MAC);
        } else {
            simpleBluetooth.connectToBluetoothServer(OLD_MAC);
        }
    }

    public void endDuel(boolean win) {
        if (win) {
            playSound(WIN_SOUND);
        } else {
            playSound(LOSS_SOUND);
            simpleBluetooth.sendData(YOU_WIN);
        }

        endGame();
    }

    @Override
    public void onBluetoothDataReceived(byte[] bytes, String s) {
        if (bytes.length > 1) {
            Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
        } else {
            final int a = bytes[0];

            switch (a) {
                case 0:
                    break;
                case YOU_WIN:
                    endDuel(true);
                    break;
                case PLAY_AGAIN:
                    startGame();
                    break;
                default:
                    action.set(a);
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (action.compareAndSet(a, 0)) {
                                takeDamage();
                                Log.i("Action", "Got hit by " + Integer.toString(a) + " ;(");
                            }
                        }
                    }, REACTION_TIME);
                    break;
            }
        }
    }

    @Override
    public void onDeviceConnected(BluetoothDevice bluetoothDevice) {
        Toast.makeText(this, "Connected!", Toast.LENGTH_SHORT).show();
        this.bluetoothDevice = bluetoothDevice;
        startGame();
    }

    @Override
    public void onDeviceDisconnected(BluetoothDevice bluetoothDevice) {
        Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
        this.bluetoothDevice = null;
        endGame();
    }

    public void startGame() {
        in_game = true;
        setHealth(MAX_HEALTH);
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
        dialog.show();
    }

    public void endGame() {
        in_game = false;
        mSensorManager.unregisterListener(this);
        dialog.dismiss();
    }

    @OnClick(R.id.star_wars)
    public void starWars() {
        star_wars = !star_wars;
    }

    @Override
    public void onDiscoveryStarted() {

    }

    @Override
    public void onDiscoveryFinished() {

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == mAccelerometer) {

//            Log.i("S", String.format("x: %3.3f, y: %3.3f, z: %3.3f", event.values[X], event.values[Y], event.values[Z]));

            int attack = 0;
            boolean[] big = new boolean[] {false, false, false};
            boolean[] small = new boolean[] {false, false, false};
            boolean cancel = false;

            for (int i = 0; i <= Z; i++) {
                if (event.values[i] >= 9.8 + ATTACK_SENSITIVITY) {
                    big[i] = true;
                }
                if (event.values[i] <= -9.8 - ATTACK_SENSITIVITY) {
                    small[i] = true;
                }
            }

            int avgZ = 0;
            for (Float[] val : lastAccels) {
                avgZ += val[Z];
            }
            avgZ /= lastAccels.size() + 1;

            if ((big[X] || lastBig[X]) && (small[Y] || lastSmall[Y])) {
                if (avgZ <= 9.8 / 2 && avgZ >= -9.8 / 2) {
                    attack = HACK_VERTICAL;
                } else if (avgZ <= -9.8 / 2) {
                    attack = HACK_LEFT;
                } else if (avgZ >= 9.8 / 2) {
                    attack = HACK_RIGHT;
                }
            } else if (big[Y] || lastBig[Y]) {
                for (Float[] val : lastAccels) {
                    if (val[Y] < -10) {
                        Log.i("S", String.format("Bad stab stopped y: %3.3f", val[Y]));
                        lastAccels.clear();
                        cancel = true;
                        break;
                    }
                }

                if (!cancel) {
                    attack = STAB;
                }
            }

            if (System.currentTimeMillis() - ACTION_DELAY >= lastActionTime) {
                switch (action.get()) {
                    case 0:
                        if (attack == 0) {
                            break;
                        }
                        if (attack == STAB) {
                            if (star_wars) {
                                playSound(random.nextInt((SWING_5 - SWING_1) + 1) + SWING_1);
                            } else {
                                playSound(ATTACK1);
                            }
                            simpleBluetooth.sendData(STAB);
                            Log.i("Attack", "Sending STAB");
                        } else {
                            if (star_wars) {
                                playSound(random.nextInt((SWING_5 - SWING_1) + 1) + SWING_1);
                            } else {
                                playSound(ATTACK2);
                            }
                            simpleBluetooth.sendData(attack);
                            Log.i("Attack", "Sending " + Integer.toString(attack));
                        }
                        lastAccels.clear();
                        lastActionTime = System.currentTimeMillis();
                        break;
                    case STAB:
                        if (Math.abs(event.values[Z]) >= 9.8 + BLOCK_SENSITIVITY || attack == STAB) {
                            action.set(0);
                            if (star_wars) {
                                playSound(random.nextInt((HIT_7 - HIT_1) + 1) + HIT_1);
                            } else {
                                playSound(random.nextInt((BLOCK4 - BLOCK1) + 1) + BLOCK1);
                            }
                            Log.i("Action", "STAB blocked!");
                            lastActionTime = System.currentTimeMillis();
                        }
                        break;
                    case HACK_RIGHT:
                        if (event.values[Z] <= -9.8 - BLOCK_SENSITIVITY || attack == HACK_RIGHT) {
                            action.set(0);
                            if (star_wars) {
                                playSound(random.nextInt((HIT_7 - HIT_1) + 1) + HIT_1);
                            } else {
                                playSound(random.nextInt((BLOCK4 - BLOCK1) + 1) + BLOCK1);
                            }
                            Log.i("Action", "HACK_RIGHT blocked!");
                            lastActionTime = System.currentTimeMillis();
                        }
                        break;
                    case HACK_LEFT:
                        if (event.values[Z] >= 9.8 + BLOCK_SENSITIVITY || attack == HACK_LEFT) {
                            action.set(0);
                            if (star_wars) {
                                playSound(random.nextInt((HIT_7 - HIT_1) + 1) + HIT_1);
                            } else {
                                playSound(random.nextInt((BLOCK4 - BLOCK1) + 1) + BLOCK1);
                            }
                            Log.i("Action", "HACK_LEFT blocked!");
                            lastActionTime = System.currentTimeMillis();
                        }
                        break;
                    case HACK_VERTICAL:
                        if (event.values[Z] <= -9.8 - BLOCK_SENSITIVITY || attack == HACK_VERTICAL) {
                            action.set(0);
                            if (star_wars) {
                                playSound(random.nextInt((HIT_7 - HIT_1) + 1) + HIT_1);
                            } else {
                                playSound(random.nextInt((BLOCK4 - BLOCK1) + 1) + BLOCK1);
                            }
                            Log.i("Action", "HACK_VERTICAL blocked!");
                            lastActionTime = System.currentTimeMillis();
                        }
                        break;
                }
            } else {
//                Log.i("Attack", "Too soon!");
            }

            lastBig = big;
            lastSmall = small;

            lastAccels.add(new Float[]{event.values[X], event.values[Y], event.values[Z]});

            if (lastAccels.size() > 10) {
                lastAccels.remove();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public void playSound(int soundID) {
        soundPool.play(soundIDs.get(soundID), 1, 1, 0, 0, 1);
    }

    public void takeDamage() {
        setHealth(health - 1);
        v.vibrate(200);
        playSound(HIT_SOUND);
    }

    public void setHealth(int new_val) {
        health = new_val;
        LinearLayout.LayoutParams param1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT, new_val);
        whiteBar.setLayoutParams(param1);

        LinearLayout.LayoutParams param2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT, MAX_HEALTH - new_val);
        redBar.setLayoutParams(param2);

        if (health == 0) {
            endDuel(false);
        }
    }
}
