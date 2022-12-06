package entity;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import engine.*;
import screen.Screen;
import engine.DrawManager.SpriteType;

/**
 * Groups enemy ships into a formation that moves together.
 *
 * @author <a href="mailto:RobertoIA1987@gmail.com">Roberto Izquierdo Amo</a>
 *
 */
public class EnemyShipFormation implements Iterable<EnemyShip> {

	/** Initial position in the x-axis. */
	private static final int INIT_POS_X = 20;
	/** Initial position in the y-axis. */
	private static final int INIT_POS_Y = 100;
	/** Distance between ships. */
	private static final int SEPARATION_DISTANCE = 40;
	/** Proportion of C-type ships. */
	private static final double PROPORTION_C = 0.2;
	/** Proportion of B-type ships. */
	private static final double PROPORTION_B = 0.4;
	/** Lateral speed of the formation. */
	private static final int X_SPEED = 8;
	/** Downwards speed of the formation. */
	private static final int Y_SPEED = 4;
	/** Speed of the bullets shot by the members. */
	private static final int BULLET_SPEED = 4;
	/** Proportion of differences between shooting times. */
	private static final double SHOOTING_VARIANCE = .2;
	/** Margin on the sides of the screen. */
	private static final int SIDE_MARGIN = 20;
	/** Margin on the bottom of the screen. */
	private static final int BOTTOM_MARGIN = 80;
	/** Distance to go down each pass. */
	private static final int DESCENT_DISTANCE = 20;
	/** Minimum speed allowed. */
	private static final int MINIMUM_SPEED = 10;

	/** DrawManager instance. */
	private DrawManager drawManager;
	/** Application logger. */
	private Logger logger;
	/** Screen to draw ships on. */
	private Screen screen;

	/** List of enemy ships forming the formation. */
	private List<List<EnemyShip>> enemyShips;
	/** Minimum time between shots. */
	private Cooldown shootingCooldown;
	/** Number of ships in the formation - horizontally. */
	private int nShipsWide;
	/** Number of ships in the formation - vertically. */
	private int nShipsHigh;
	/** Time between shots. */
	private int shootingInterval;
	/** Variance in the time between shots. */
	private int shootingVariance;
	/** Initial ship speed. */
	private int baseSpeed;
	/** Speed of the ships. */
	private int movementSpeed;
	/** Current direction the formation is moving on. */
	private Direction currentDirection;
	/** Direction the formation was moving previously. */
	private Direction previousDirection;
	/** Interval between movements, in frames. */
	private int movementInterval;
	/** Total width of the formation. */
	private int width;
	/** Total height of the formation. */
	private int height;
	/** Position in the x-axis of the upper left corner of the formation. */
	private int positionX;
	/** Position in the y-axis of the upper left corner of the formation. */
	private int positionY;
	/** Width of one ship. */
	private int shipWidth;
	/** Height of one ship. */
	private int shipHeight;
	/** List of ships that are able to shoot. */
	private List<EnemyShip> shooters;
	/** Number of not destroyed ships. */
	public static int shipCount;

	private int columnCount;

	private boolean checkFirstLine = false;


	/** Directions the formation can move. */
	private enum Direction {
		/** Movement to the right side of the screen. */
		RIGHT,
		/** Movement to the left side of the screen. */
		LEFT,
		/** Movement to the bottom of the screen. */
		DOWN
	};

	/**
	 * Constructor, sets the initial conditions.
	 *
	 * @param gameSettings
	 *            Current game settings.
	 */
	public EnemyShipFormation(final GameSettings gameSettings) {
		this.drawManager = Core.getDrawManager();
		this.logger = Core.getLogger();
		this.enemyShips = new ArrayList<List<EnemyShip>>();
		this.currentDirection = Direction.RIGHT;
		this.movementInterval = 0;
		this.nShipsWide = gameSettings.getFormationWidth();
		this.nShipsHigh = gameSettings.getFormationHeight();
		this.shootingInterval = gameSettings.getShootingFrecuency();
		this.shootingVariance = (int) (gameSettings.getShootingFrecuency()
				* SHOOTING_VARIANCE);
		this.baseSpeed = gameSettings.getBaseSpeed();
		this.movementSpeed = this.baseSpeed;
		this.positionX = INIT_POS_X;
		this.positionY = INIT_POS_Y;
		this.shooters = new ArrayList<EnemyShip>();
		SpriteType spriteType;

		this.logger.info("Initializing " + nShipsWide + "x" + nShipsHigh
				+ " ship formation in (" + positionX + "," + positionY + ")");

		// Each sub-list is a column on the formation.
		for (int i = 0; i < this.nShipsWide; i++)
			this.enemyShips.add(new ArrayList<EnemyShip>());

		for (List<EnemyShip> column : this.enemyShips) {
			for (int i = 0; i < this.nShipsHigh; i++) {
				if (i / (float) this.nShipsHigh < PROPORTION_C)
					spriteType = SpriteType.EnemyShipC1;
				else if (i / (float) this.nShipsHigh < PROPORTION_B
						+ PROPORTION_C)
					spriteType = SpriteType.EnemyShipB1;
				else
					spriteType = SpriteType.EnemyShipA1;

				column.add(new EnemyShip((SEPARATION_DISTANCE
						* this.enemyShips.indexOf(column))
								+ positionX, (SEPARATION_DISTANCE * i)
								+ positionY, spriteType));
				this.shipCount++;
			}
		}

		this.shipWidth = this.enemyShips.get(0).get(0).getWidth();
		this.shipHeight = this.enemyShips.get(0).get(0).getHeight();

		this.width = (this.nShipsWide - 1) * SEPARATION_DISTANCE
				+ this.shipWidth;
		this.height = (this.nShipsHigh - 1) * SEPARATION_DISTANCE
				+ this.shipHeight;

		for (List<EnemyShip> column : this.enemyShips)
			this.shooters.add(column.get(column.size() - 1));
	}

	/**
	 * Associates the formation to a given screen.
	 *
	 * @param newScreen
	 *            Screen to attach.
	 */
	public final void attach(final Screen newScreen) {
		screen = newScreen;
	}

	/**
	 * Draws every individual component of the formation.
	 */
	public final void draw() {
		for (List<EnemyShip> column : this.enemyShips)
			for (EnemyShip enemyShip : column)
				drawManager.drawEntity(enemyShip, enemyShip.getPositionX(),
						enemyShip.getPositionY());
	}

	/**
	 * Updates the position of the ships.
	 */
	public final void update() {
		if(this.shootingCooldown == null) {
			this.shootingCooldown = Core.getVariableCooldown(shootingInterval,
					shootingVariance);
			this.shootingCooldown.reset();
		}

		cleanUp();

		int movementX = 0;
		int movementY = 0;
		double remainingProportion = (double) this.shipCount
				/ (this.nShipsHigh * this.nShipsWide);
		this.movementSpeed = (int) (Math.pow(remainingProportion, 2)
				* this.baseSpeed);
		this.movementSpeed += MINIMUM_SPEED;
		movementInterval++;
		if (movementInterval >= this.movementSpeed) {
			movementInterval = 0;

			boolean isAtTop = positionY +
					this.height<screen.getHeight()-BOTTOM_MARGIN;
			boolean isAtBottom = positionY
					+ this.height > screen.getHeight() - BOTTOM_MARGIN;
			boolean isAtRightSide = positionX
					+ this.width >= screen.getWidth() - SIDE_MARGIN;
			boolean isAtLeftSide = positionX <= SIDE_MARGIN;
			boolean isAtHorizontalAltitude = positionY % DESCENT_DISTANCE == 0;

			if (currentDirection == Direction.DOWN) {
				if (isAtHorizontalAltitude)
					if (previousDirection == Direction.RIGHT) {
						currentDirection = Direction.LEFT;
						this.logger.info("Formation now moving left 1");
					} else {
						currentDirection = Direction.RIGHT;
						this.logger.info("Formation now moving right 2");
					}
			} else if (currentDirection == Direction.LEFT) {
				if (isAtLeftSide)
					if (!isAtBottom && movementY != 0) {
						previousDirection = currentDirection;
						currentDirection = Direction.DOWN;
						this.logger.info("Formation now moving down 3");
					} else {
						currentDirection = Direction.RIGHT;
						this.logger.info("Formation now moving right 4");
					}
			} else {
				if (isAtRightSide)
					if (!isAtBottom && movementY != 0) {
						previousDirection = currentDirection;
						currentDirection = Direction.DOWN;
						this.logger.info("Formation now moving down 5");
					} else {
						currentDirection = Direction.LEFT;
						this.logger.info("Formation now moving left 6");
					}
			}

			if (currentDirection == Direction.RIGHT)
				movementX = X_SPEED;
			else if (currentDirection == Direction.LEFT)
				movementX = -X_SPEED;
			else
				movementY = Y_SPEED;

			positionX += movementX;
			positionY += movementY;

			// Cleans explosions.
			List<EnemyShip> destroyed;
			for (List<EnemyShip> column : this.enemyShips) {
				destroyed = new ArrayList<EnemyShip>();
				for (EnemyShip ship : column) {
					if (ship != null && ship.isDestroyed()) {
						destroyed.add(ship);
						this.logger.info("Removed enemy "
								+ column.indexOf(ship) + " from column "
								+ this.enemyShips.indexOf(column));
					}
				}
				column.removeAll(destroyed);
			}

			for (List<EnemyShip> column : this.enemyShips)
				for (EnemyShip enemyShip : column) {
					if(isLast()){
						if(!isAtTop) {
							movementY = -30;
							enemyShip.move(movementX, movementY);
						}
						else if(!isAtBottom){
							movementY = (int) (Math.random() * Y_SPEED + Y_SPEED);
							enemyShip.move(movementX,movementY);
						}
					}else {
						if (!isAtBottom) {
							int randomPlace = (int) (Math.random() * column.size() - 1);
							movementY = 1;
							if (Math.random() < 0.70) {
								if (randomPlace < enemyShips.size()) {
									if (enemyShips.get(randomPlace) == column && column.get(column.size() - 1) == enemyShip) {
										movementY = (int) (Math.random() * Y_SPEED + Y_SPEED);
									}
								}
							} else {
								if (enemyShips.get(enemyShips.size() - 1) == column && column.get(column.size() - 1) == enemyShip) {
									movementY = (int) (Math.random() * Y_SPEED + Y_SPEED);
								}
							}
						}
						enemyShip.move(movementX, movementY);
						enemyShip.update();
					}
				}

			//마지막줄 남으면 더이상 색 변화 x
			for (List<EnemyShip> column : this.enemyShips) {
				for (EnemyShip enemyShip : column)
					enemyShip.setColor(Color.white);
			}

			int randomPlace_r = (int) (Math.random() * enemyShips.size() - 1);
			int randomPlace_c = (int) (Math.random() * enemyShips.get(randomPlace_r).size() - 1);
			if(this.shipCount>nShipsWide) {
				if (enemyShips.get(randomPlace_r).get(randomPlace_c) != null)
					enemyShips.get(randomPlace_r).get(randomPlace_c).changeColor();
			}
			//목숨 여러개인 적 색상 변화
			for (List<EnemyShip> column : this.enemyShips) {
				for (EnemyShip enemyShip : column)
					 enemyShip.changeColor_G(enemyShip.getEnemyLives());
			}
			//마지막 적 색상 변화
			if(isLast()) changeLastEnemy();
		}
	}

	/**
	 * Cleans empty columns, adjusts the width and height of the formation.
	 */
	private void cleanUp() {
		Set<Integer> emptyColumns = new HashSet<Integer>();
		int maxColumn = 0;
		int minPositionY = Integer.MAX_VALUE;
		for (List<EnemyShip> column : this.enemyShips) {
			if (!column.isEmpty()) {
				// Height of this column
				int columnSize = column.get(column.size() - 1).positionY
						- this.positionY + this.shipHeight;
				maxColumn = Math.max(maxColumn, columnSize);
				minPositionY = Math.min(minPositionY, column.get(0)
						.getPositionY());
			} else {
				// Empty column, we remove it.
				emptyColumns.add(this.enemyShips.indexOf(column));
			}
		}
		for (int index : emptyColumns) {
			this.enemyShips.remove(index);
			logger.info("Removed column " + index);

		}

		int leftMostPoint = 0;
		int rightMostPoint = 0;

		for (List<EnemyShip> column : this.enemyShips) {
			if (!column.isEmpty()) {
				if (leftMostPoint == 0)
					leftMostPoint = column.get(0).getPositionX();
				rightMostPoint = column.get(0).getPositionX();
			}
		}

		this.width = rightMostPoint - leftMostPoint + this.shipWidth;
		this.height = maxColumn;

		this.positionX = leftMostPoint;
		this.positionY = minPositionY;
	}

	/**
	 * Shoots a bullet downwards.
	 *
	 * @param bullets
	 *            Bullets set to add the bullet being shot.
	 */
	public final void shoot(final Set<Bullet> bullets) {

		// For now, only ships in the bottom row are able to shoot.
		int index = (int) (Math.random() * this.shooters.size());
		EnemyShip shooter = this.shooters.get(index);

		if (this.shootingCooldown.checkFinished()) {
			new Sound().bulletsound();
			this.shootingCooldown.reset();
			float ShootPattern = (float)(Math.round(Math.random()*10)/10.0);
			if (isLast()) { // The last enemy can get the all ShootPattern.
				bullets.add(BulletPool.getBullet(shooter.getPositionX()
						+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED,0));
				bullets.add(BulletPool.getBullet(shooter.getPositionX()
						+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED * 2,0));
				bullets.add(BulletPool.getBullet(shooter.getPositionX()
						+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED,1));
				bullets.add(BulletPool.getBullet(shooter.getPositionX()
						+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED * 2,1));
				bullets.add(BulletPool.getBullet(shooter.getPositionX()
						+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED,2));
				bullets.add(BulletPool.getBullet(shooter.getPositionX()
						+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED * 2,2));
			}
			else if(ShootPattern<=0.4) { //The Enemy of double Bullet Type
				bullets.add(BulletPool.getBullet(shooter.getPositionX()
						+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED,0));
				bullets.add(BulletPool.getBullet(shooter.getPositionX()
						+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED * 2,0));
			}
			else if(0.4 < ShootPattern && ShootPattern < 0.7) {//shoot double direction
				bullets.add(BulletPool.getBullet(shooter.getPositionX()
						+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED,1));
				bullets.add(BulletPool.getBullet(shooter.getPositionX()
						+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED,2));
			}
			else{
				bullets.add(BulletPool.getBullet(shooter.getPositionX()//general shoot
						+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED,0));
			}
		}
	}

	public final void shootN(final Set<BulletN> bulletsN) {
		// For now, only ships in the bottom row are able to shoot.
		int index = (int) (Math.random() * this.shooters.size());
		EnemyShip shooter = this.shooters.get(index);
		if (isLast()) { // The last enemy can get the all ShootPattern.
			bulletsN.add(BulletPool.getBulletN(shooter.getPositionX()
					+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED,0));
			bulletsN.add(BulletPool.getBulletN(shooter.getPositionX()
					+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED * 2,0));
			bulletsN.add(BulletPool.getBulletN(shooter.getPositionX()
					+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED,1));
			bulletsN.add(BulletPool.getBulletN(shooter.getPositionX()
					+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED * 2,1));
			bulletsN.add(BulletPool.getBulletN(shooter.getPositionX()
					+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED,2));
			bulletsN.add(BulletPool.getBulletN(shooter.getPositionX()
					+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED * 2,2));
		}
		else if (this.shootingCooldown.checkFinished()) {
			new Sound().bulletsound();
			this.shootingCooldown.reset();
			float ShootPattern = (float)(Math.round(Math.random()*10)/10.0);
			if (isLast()) { // The last enemy can get the all ShootPattern.
				bulletsN.add(BulletPool.getBulletN(shooter.getPositionX()
						+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED,0));
				bulletsN.add(BulletPool.getBulletN(shooter.getPositionX()
						+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED * 2,0));
				bulletsN.add(BulletPool.getBulletN(shooter.getPositionX()
						+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED,1));
				bulletsN.add(BulletPool.getBulletN(shooter.getPositionX()
						+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED * 2,1));
				bulletsN.add(BulletPool.getBulletN(shooter.getPositionX()
						+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED,2));
				bulletsN.add(BulletPool.getBulletN(shooter.getPositionX()
						+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED * 2,2));
			}
			else if(ShootPattern<=0.4) {//The Enemy of double Bullet Type
				bulletsN.add(BulletPool.getBulletN(shooter.getPositionX()
						+ shooter.width / 2, shooter.getPositionY(),BULLET_SPEED,0));
				bulletsN.add(BulletPool.getBulletN(shooter.getPositionX()
						+ shooter.width / 2, shooter.getPositionY(),BULLET_SPEED* 2,0));
			}
			else if(0.4 < ShootPattern && ShootPattern < 0.7) {//shoot double direction
				bulletsN.add(BulletPool.getBulletN(shooter.getPositionX()
						+ shooter.width / 2, shooter.getPositionY(),BULLET_SPEED,1));
				bulletsN.add(BulletPool.getBulletN(shooter.getPositionX()
						+ shooter.width / 2, shooter.getPositionY(),BULLET_SPEED,2));
			}
			else{
				bulletsN.add(BulletPool.getBulletN(shooter.getPositionX()//general shoot
						+ shooter.width / 2, shooter.getPositionY(),BULLET_SPEED,0));
			}
		}
	}

	public final void shootH(final Set<BulletH> bulletsH) {
		// For now, only ships in the bottom row are able to shoot.
		int index = (int) (Math.random() * this.shooters.size());
		EnemyShip shooter = this.shooters.get(index);
		if (isLast()) { // The last enemy can get the all ShootPattern.
			bulletsH.add(BulletPool.getBulletH(shooter.getPositionX()
					+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED,0));
			bulletsH.add(BulletPool.getBulletH(shooter.getPositionX()
					+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED * 2,0));
			bulletsH.add(BulletPool.getBulletH(shooter.getPositionX()
					+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED,1));
			bulletsH.add(BulletPool.getBulletH(shooter.getPositionX()
					+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED * 2,1));
			bulletsH.add(BulletPool.getBulletH(shooter.getPositionX()
					+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED,2));
			bulletsH.add(BulletPool.getBulletH(shooter.getPositionX()
					+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED * 2,2));
		}
		else if (this.shootingCooldown.checkFinished()) {

			this.shootingCooldown.reset();
			float ShootPattern = (float)(Math.round(Math.random()*10)/10.0);
			if(ShootPattern<=0.4) { //The Enemy of double Bullet Type
				new Sound().bulletsound();
				bulletsH.add(BulletPool.getBulletH(shooter.getPositionX()
						+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED,0));
				bulletsH.add(BulletPool.getBulletH(shooter.getPositionX()
						+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED * 2,0));
			}
			else if(0.4 < ShootPattern && ShootPattern < 0.7) {//shoot double direction
				new Sound().bulletsound();
				bulletsH.add(BulletPool.getBulletH(shooter.getPositionX()
						+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED,1));
				bulletsH.add(BulletPool.getBulletH(shooter.getPositionX()
						+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED,2));
			}
			else{
				new Sound().bulletsound();
				bulletsH.add(BulletPool.getBulletH(shooter.getPositionX()//general shoot
						+ shooter.width / 2, shooter.getPositionY(), BULLET_SPEED,0));
			}
		}
	}

	/**
	 * Destroys a ship.
	 *
	 * @param destroyedShip
	 *            Ship to be destroyed.
	 */
	public final void destroy(final EnemyShip destroyedShip) {
		for (List<EnemyShip> column : this.enemyShips)
			for (int i = 0; i < column.size(); i++) {

				if (column.get(i).equals(destroyedShip)) {
					new Sound().explosionsound();
					column.get(i).destroy();
					this.logger.info("Destroyed ship in ("
							+ this.enemyShips.indexOf(column) + "," + i + ")");
					this.shipCount--;
				}
			}

		// Updates the list of ships that can shoot the player.
		if (this.shooters.contains(destroyedShip)) {
			int destroyedShipIndex = this.shooters.indexOf(destroyedShip);
			int destroyedShipColumnIndex = -1;

			for (List<EnemyShip> column : this.enemyShips)
				if (column.contains(destroyedShip)) {
					destroyedShipColumnIndex = this.enemyShips.indexOf(column);
					break;
				}

			EnemyShip nextShooter = getNextShooter(this.enemyShips
					.get(destroyedShipColumnIndex));

			if (nextShooter != null)
				this.shooters.set(destroyedShipIndex, nextShooter);
			else {
				this.shooters.remove(destroyedShipIndex);
				this.logger.info("Shooters list reduced to "
						+ this.shooters.size() + " members.");
			}
		}


	}

	/**
	 * Gets the ship on a given column that will be in charge of shooting.
	 * 
	 * @param column
	 *            Column to search.
	 * @return New shooter ship.
	 */
	public final EnemyShip getNextShooter(final List<EnemyShip> column) {
		Iterator<EnemyShip> iterator = column.iterator();
		EnemyShip nextShooter = null;
		while (iterator.hasNext()) {
			EnemyShip checkShip = iterator.next();
			if (checkShip != null && !checkShip.isDestroyed())
				nextShooter = checkShip;
		}

		return nextShooter;
	}

	/**
	 * Returns an iterator over the ships in the formation.
	 * 
	 * @return Iterator over the enemy ships.
	 */
	@Override
	public final Iterator<EnemyShip> iterator() {
		Set<EnemyShip> enemyShipsList = new HashSet<EnemyShip>();

		for (List<EnemyShip> column : this.enemyShips)
			for (EnemyShip enemyShip : column)
				enemyShipsList.add(enemyShip);

		return enemyShipsList.iterator();
	}

	/**
	 * Checks if there are any ships remaining.
	 * 
	 * @return True when all ships have been destroyed.
	 */
	public final boolean isEmpty() {
		return this.shipCount <= 0;
	}

	/**
	 * Checks if there are any ships remaining.
	 *
	 * @return True when last one ships have been leaved.
	 */
	public final boolean isLast() {
		return this.shipCount == 1;
	}
	private void changeLastEnemy(){
		for (List<EnemyShip> column : this.enemyShips){
			for(EnemyShip enemyShip : column)
				enemyShip.changeColor_Last();
		}
	}

	public boolean isFirstLine(final EnemyShip destroyedShip){
		System.out.println("Start new");
		for (List<EnemyShip> column : this.enemyShips) {
			//6줄 중 0이 먼저 죽으면 size는 5이고 i = 1 ~ 6인가?
			//맞네 012345중에 4가 죽으면 column은 01234로 바뀌지만 실질적으로 있는건 0123 5임
			//근데 코드상으로 똑같은거 아닌가? 없어지면 없어진대로 작동되는거 아닌가
			System.out.println("Column = " + column);
			for (int i = 0; i < column.size(); i++) {
				System.out.println(i);
				if (i == 0) {
					System.out.println(destroyedShip);
					System.out.println(column.get(i));
					System.out.println(column.get(i).equals(destroyedShip));
					if (column.get(i).equals(destroyedShip)) {
						checkFirstLine = true;
						System.out.println("first");
						return checkFirstLine;
					} else {
						checkFirstLine = false;
						System.out.println("notFirst");
					}
				}
			}
		}
		return checkFirstLine;
	}
}
