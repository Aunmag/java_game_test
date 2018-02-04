package aunmag.shooter.environment.actor;

import aunmag.nightingale.audio.AudioSample;
import aunmag.nightingale.audio.AudioSampleType;
import aunmag.nightingale.audio.AudioSource;
import aunmag.nightingale.utilities.FluidToggle;
import aunmag.shooter.client.graphics.CameraShaker;
import aunmag.shooter.data.LinksKt;
import aunmag.shooter.environment.actor.components.Hands;
import aunmag.shooter.environment.actor.components.Stamina;
import aunmag.shooter.environment.weapon.Weapon;
import aunmag.nightingale.utilities.FluidValue;
import aunmag.nightingale.utilities.UtilsMath;
import aunmag.nightingale.collision.CollisionCircle;
import aunmag.shooter.environment.World;

public class Actor extends CollisionCircle {

    private static final int[] samples = new int[6];
    private static final float velocityFactorAside = 0.6f;
    private static final float velocityFactorBack = 0.8f;
    private static int indexOfLastCollisionCheckedActor = 0;

    public final World world;
    public final ActorType type;
    private float health = 1.0f;
    public final Stamina stamina;
    private int kills = 0;
    private Weapon weapon = null;
    private Hands hands;
    private AudioSource audioSource = new AudioSource();

    private FluidValue offsetRadians;
    public boolean isWalkingForward = false;
    public boolean isWalkingBack = false;
    public boolean isWalkingLeft = false;
    public boolean isWalkingRight = false;
    public boolean isSprinting = false;
    public boolean isAttacking = false;
    public final FluidToggle isAiming;

    static {
        for (int i = 0; i < samples.length; i++) {
            String sampleName = "sounds/actors/human_hurt_" + (i + 1);
            samples[i] = AudioSample.getOrCreate(sampleName, AudioSampleType.OGG);
        }
    }

    public Actor(ActorType type, World world) {
        super(0, 0, 0.225f);
        this.type = type;
        this.world = world;
        hands = new Hands(this);
        stamina = new Stamina(this);

        offsetRadians = new FluidValue(world.getTime(), 0.06f);
        offsetRadians.setFlexDegree(0.5f);

        isAiming = new FluidToggle(world.getTime(), 0.25f);
        isAiming.setFlexDegree(1.25f);
    }

    public void update() {
        if (!isAlive()) {
            remove();
            return;
        }

        offsetRadians.update();
        if (offsetRadians.getTarget() != 0 && offsetRadians.isTargetReached()) {
            addRadiansCarefully(offsetRadians.getCurrent());
            offsetRadians.setTarget(0);
            offsetRadians.reachTargetNow();
        }

        updateStamina();
        isAiming.update();
        walk();
        updateCollision();
        hands.update();
        updateWeapon();
    }

    private void updateStamina() {
        stamina.update();
        float spend = isAiming.getCurrent() / 2.0f;

        if (isWalking()) {
            spend += 0.7f;
            if (isSprinting) {
                spend += 1.8f;
            }
        }

        if (weapon != null && weapon.magazine.isReloading()) {
            spend += 0.2f;
        }

        if (spend != 0.0) {
            stamina.spend(spend);
        }
    }

    private void updateCollision() {
        for (int index = world.getActors().size() - 1; index >= 0; index--) {
            if (index == indexOfLastCollisionCheckedActor) {
                break;
            }

            Actor opponent = world.getActors().get(index);

            if (!opponent.isAlive() || opponent.isRemoved() || opponent == this) {
                continue;
            }

            preventCollisionWith(opponent);
        }

        indexOfLastCollisionCheckedActor++;
    }

    private void updateWeapon() {
        if (weapon == null) {
            return;
        }

        if (isAttacking) {
            weapon.trigger.pressBy(this);
        } else {
            weapon.trigger.release();
        }

        weapon.update();
    }

    private void updateWeaponPosition() {
        if (weapon != null) {
            weapon.setRadians(getRadians());
            float x = getX() + 0.375f * (float) Math.cos(getRadians());
            float y = getY() + 0.375f * (float) Math.sin(getRadians());
            weapon.setPosition(x, y);
        }
    }

    private void walk() {
        if (isWalkingForward) {
            move(type.velocity, 0);
        }

        if (isWalkingBack) {
            move(type.velocity * velocityFactorBack, (float) -Math.PI);
        }

        if (isWalkingLeft) {
            move(type.velocity * velocityFactorAside, (float) UtilsMath.PIx0_5);
        }

        if (isWalkingRight) {
            move(type.velocity * velocityFactorAside, (float) -UtilsMath.PIx0_5);
        }
    }

    private void move(double velocity, float radiansTurn) {
        if (isSprinting && isWalkingForward) {
            float efficiency = this.stamina.calculateEfficiency();
            velocity *= type.velocityFactorSprint * efficiency + (1 - efficiency);
        }

        velocity -= velocity * isAiming.getCurrent() / 2f;
        velocity *= health;
        velocity *= world.getTime().getDelta();

        float moveX = (float) (velocity * Math.cos(getRadians() + radiansTurn));
        float moveY = (float) (velocity * Math.sin(getRadians() + radiansTurn));
        addPosition(moveX, moveY);
    }

    public void hit(float intensity, Actor attacker) {
        intensity /= type.strength;

        boolean wasDeadBefore = !isAlive();
        addHealth(-intensity);

        if (!wasDeadBefore && !isAlive() && attacker != null) {
            attacker.increaseKills();
        }

        push(UtilsMath.random.nextBoolean() ? intensity : -intensity);
    }

    public void push(float force) {
        offsetRadians.setTarget(force);

        if (this == LinksKt.getPlayer()) {
            CameraShaker.shake(force);
        }
    }

    public void render() {
        hands.render();
        super.render();
    }

    public void remove() {
        if (isRemoved()) {
            return;
        }

        if (weapon != null) {
            weapon.remove();
        }

        super.remove();
    }

    private void soundHurt() {
        if (type != ActorType.human) {
            return;
        }

        if (audioSource.isPlaying()) {
            return;
        }

        int sample = samples[UtilsMath.random.nextInt(samples.length)];
        audioSource.setSample(sample);
        audioSource.play();
    }

    private void increaseKills() {
        kills++;
    }

    public static void finalizeUpdate() {
        indexOfLastCollisionCheckedActor = 0;
    }

    /* Setters */

    private void addHealth(float addHealth) {
        health = UtilsMath.limitNumber(health + addHealth, 0, 1);

        if (!isAlive()) {
            remove();
        } else if (addHealth < -0.005) {
            soundHurt();
        }
    }

    public void setPosition(float x, float y) {
        super.setPosition(x, y);

        hands.updatePosition();
        audioSource.setPosition(getX(), getY());
        updateWeaponPosition();
    }

    public void setRadians(float radians) {
        super.setRadians(radians);
        updateWeaponPosition(); // TODO: Optimize
    }

    public void setWeapon(Weapon weapon) {
        this.weapon = weapon;
    }

    /* Getters */

    public float getRadians() {
        if (offsetRadians != null) {
            return super.getRadians() + offsetRadians.getCurrent();
        } else {
            return super.getRadians();
        }
    }

    public float getHealth() {
        return health;
    }

    public boolean isAlive() {
        return health > 0;
    }

    public boolean isWalking() {
        return isWalkingForward || isWalkingBack || isWalkingLeft || isWalkingRight;
    }

    public boolean getHasWeapon() {
        return weapon != null;
    }

    public Hands getHands() {
        return hands;
    }

    public int getKills() {
        return kills;
    }

    public Weapon getWeapon() {
        return weapon;
    }

}
