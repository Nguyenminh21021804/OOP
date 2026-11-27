package traps;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.IUpdateable;
import de.gurkenlabs.litiengine.environment.Environment;
import de.gurkenlabs.litiengine.graphics.RenderType;
import player.Player;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * Vật phẩm bình máu / bình mana đặt trên bản đồ.
 * Khi nhân vật chạm vào, sẽ hồi HP hoặc Mana tương ứng và biến mất khỏi map.
 */
public class PotionPickupEntity extends TiledObjectEntity implements IUpdateable {

    public enum PotionKind {
        HEALTH,
        MANA
    }

    private final Environment env;
    private final Player player;
    private final PotionKind kind;
    private final int amount;
    private final Rectangle2D pickupBounds;

    private boolean collected = false;
    private boolean wasTouchingWhileFull = false;

    public PotionPickupEntity(
            Environment env,
            Player player,
            PotionKind kind,
            int amount,
            double anchorX,
            double anchorY,
            double w,
            double h,
            BufferedImage[] frames,
            int[] frameDurations,
            boolean flipH,
            boolean flipV,
            double rotationDeg
    ) {
        super(anchorX, anchorY, w, h, frames, frameDurations, flipH, flipV, rotationDeg, RenderType.NORMAL);
        this.env = env;
        this.player = player;
        this.kind = kind;
        this.amount = Math.max(1, amount);
        double bw = Math.max(1.0, w);
        double bh = Math.max(1.0, h);
        this.pickupBounds = new Rectangle2D.Double(anchorX, anchorY - bh, bw, bh);
    }

    @Override
    public void update() {
        if (collected || player == null || player.isDead() || player.getState() == Player.State.FALL) {
            return;
        }

        Rectangle2D body = player.getHitbox().getBodyBox();
        if (body == null || !body.intersects(pickupBounds)) {
            wasTouchingWhileFull = false;
            return;
        }

        if (kind == PotionKind.HEALTH) {
            if (player.getHp() >= player.getMaxHp()) {
                if (!wasTouchingWhileFull) {
                    player.showNotice("Mau da day (" + player.getHp() + "/" + player.getMaxHp() + ")");
                    wasTouchingWhileFull = true;
                }
                return;
            }
            int gained = player.heal(amount);
            if (gained > 0) {
                collected = true;
                player.showNotice("+" + gained + " Mau (" + player.getHp() + "/" + player.getMaxHp() + ")");
                removeSelf();
            }
        } else {
            if (player.getMana() >= player.getMaxMana()) {
                if (!wasTouchingWhileFull) {
                    player.showNotice("Mana da day (" + player.getMana() + "/" + player.getMaxMana() + ")");
                    wasTouchingWhileFull = true;
                }
                return;
            }
            int before = player.getMana();
            player.restoreMana(amount);
            int gained = player.getMana() - before;
            if (gained > 0) {
                collected = true;
                player.showNotice("+" + gained + " Mana (" + player.getMana() + "/" + player.getMaxMana() + ")");
                removeSelf();
            }
        }
    }

    private void removeSelf() {
        Environment targetEnv = (env != null) ? env : Game.world().environment();
        if (targetEnv != null) {
            targetEnv.remove(this);
        }
    }

    public PotionKind getKind() {
        return kind;
    }

    public int getAmount() {
        return amount;
    }

    public boolean isCollected() {
        return collected;
    }
}
