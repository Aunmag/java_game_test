package aunmag.shooter.environment.actor;

import aunmag.nightingale.audio.AudioSample;
import aunmag.nightingale.audio.AudioSampleType;
import aunmag.nightingale.audio.AudioSource;
import aunmag.nightingale.math.CollisionCC;
import aunmag.nightingale.math.BodyCircle;
import aunmag.nightingale.math.Kinetics;
import aunmag.nightingale.utilities.FluidToggle;
import aunmag.nightingale.utilities.Operative;
import aunmag.nightingale.utilities.UtilsMath;
import aunmag.shooter.Config;
import aunmag.shooter.client.App;
import aunmag.shooter.client.graphics.CameraShaker;
import aunmag.shooter.data.LinksKt;
import aunmag.shooter.environment.World;
import aunmag.shooter.environment.weapon.Weapon;

public class Actor extends Operative {

    @Config public static final float VELOCITY_FACTOR_ASIDE = 0.6f;
    @Config public static final float VELOCITY_FACTOR_BACK = 0.8f;
    @Config public static final float AIMING_TIME = 0.25f;
    @Config public static final float AIMING_FLEX = 1.25f;
    @Config public static final float AIMING_VELOCITY_AFFECT = 0.5f;
    @Config public static final float AIMING_STAMINA_COST = 0.5f;
    @Config public static final float WALKING_STAMINA_COST = 0.7f;
    @Config public static final float SPRINT_STAMINA_COST = 1.8f;
    @Config public static final float RELOADING_STAMINA_COST = 0.2f;
    @Config public static final float PAIN_THRESHOLD = 0.005f;

    private static final int[] samples = new int[6];

    public final World world;
    public final ActorType type;
    public final BodyCircle body;
    public final Kinetics kinetics;
    private float health = 1.0f;
    public final Stamina stamina;
    private int kills = 0;
    private Weapon weapon = null;
    public final Hands hands;
    private AudioSource audioSource = new AudioSource();

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

    public Actor(ActorType type, World world, float x, float y, float radians) {
        this.type = type;
        this.world = world;
        body = new BodyCircle(x, y, radians, type.radius);
        hands = new Hands(this);
        stamina = new Stamina(this);

        isAiming = new FluidToggle(world.getTime(), AIMING_TIME);
        isAiming.setFlexDegree(AIMING_FLEX);

        kinetics = new Kinetics(type.weight);
    }

    public void update() {
        if (!isAlive()) {
            remove();
            return;
        }

        updateStamina();
        isAiming.update();
        walk();
        updateKinetics();
        updateCollision();
        hands.update();
        updateWeapon();
    }

    private void updateStamina() {
        stamina.update();
        float spend = AIMING_STAMINA_COST * isAiming.getCurrent();

        if (isWalking()) {
            spend += WALKING_STAMINA_COST;
            if (isSprinting) {
                spend += SPRINT_STAMINA_COST;
            }
        }

        if (weapon != null && weapon.magazine.isReloading()) {
            spend += RELOADING_STAMINA_COST;
        }

        if (spend != 0.0) {
            stamina.spend(spend);
        }
    }

    protected void updateKinetics() {
        float timeDelta = (float) world.getTime().getDelta();
        kinetics.update(timeDelta);

        float velocityX = kinetics.velocity.x * timeDelta;
        float velocityY = kinetics.velocity.y * timeDelta;
        body.position.add(velocityX, velocityY);
        body.radians += kinetics.velocityRadians * timeDelta;
    }

    private void updateCollision() {
        for (Actor opponent: world.getActors().all) {
            if (opponent != this) {
                CollisionCC collision = new CollisionCC(body, opponent.body);

                if (collision.isTrue()) {
                    Kinetics.interact(kinetics, opponent.kinetics);
                    collision.resolve();
                }
            }
        }
    }

    private void updateWeapon() {
        if (weapon == null) {
            return;
        }

        weapon.body.radians = body.radians;

        float offset = Hands.DISTANCE;
        offset += weapon.type.length / 2;
        offset -= weapon.type.gripOffset;

        weapon.body.positionTail.set(
                body.position.x + offset * (float) Math.cos(body.radians),
                body.position.y + offset * (float) Math.sin(body.radians)
        );

        if (isAttacking) {
            weapon.trigger.pressBy(this);
        } else {
            weapon.trigger.release();
        }

        weapon.update();
    }

    private void walk() {
        if (isWalkingForward) {
            move(type.velocity, 0);
        }

        if (isWalkingBack) {
            move(type.velocity * VELOCITY_FACTOR_BACK, -Math.PI);
        }

        if (isWalkingLeft) {
            move(type.velocity * VELOCITY_FACTOR_ASIDE, +UtilsMath.PIx0_5);
        }

        if (isWalkingRight) {
            move(type.velocity * VELOCITY_FACTOR_ASIDE, -UtilsMath.PIx0_5);
        }
    }

    private void move(double velocity, double radiansTurn) {
        if (isSprinting && isWalkingForward) {
            float efficiency = this.stamina.calculateEfficiency();
            velocity *= type.velocityFactorSprint * efficiency + (1 - efficiency);
        }

        velocity -= velocity * isAiming.getCurrent() * AIMING_VELOCITY_AFFECT;
        velocity *= health;

        float moveX = (float) (velocity * Math.cos(body.radians + radiansTurn));
        float moveY = (float) (velocity * Math.sin(body.radians + radiansTurn));
        float timeDelta = (float) world.getTime().getDelta();
        kinetics.addEnergy(moveX, moveY, 0, timeDelta);
    }

    public void hit(float damage, Actor attacker) {
        damage /= type.strength;

        boolean wasAlive = isAlive();

        addHealth(-damage);

        if (wasAlive && !isAlive() && attacker != null) {
            attacker.increaseKills();
        }

        push(UtilsMath.random.nextBoolean() ? damage : -damage);
    }

    public void push(float force) {
        kinetics.velocityRadians += force * 8f;

        if (this == LinksKt.getPlayer()) {
            CameraShaker.shake(force);
        }
    }

    public void render() {
        if (weapon != null) {
            weapon.render();
        }

        if (App.main.isDebug()) {
            body.render();
            hands.coverage.render();
        } else {
            type.texture.renderOnWorld(
                    body.position.x,
                    body.position.y,
                    body.radians
            );
        }
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

    /* Setters */

    private void addHealth(float addHealth) {
        health = UtilsMath.limitNumber(health + addHealth, 0, 1);

        if (!isAlive()) {
            remove();
        } else if (addHealth < -PAIN_THRESHOLD) {
            soundHurt();
        }
    }

    public void setWeapon(Weapon weapon) {
        this.weapon = weapon;
    }

    /* Getters */

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

    public int getKills() {
        return kills;
    }

    public Weapon getWeapon() {
        return weapon;
    }

}
