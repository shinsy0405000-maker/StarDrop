import java.awt.Color;
import java.util.ArrayList;

/**
 * 비행을 진행하는 층: 조준선 미리 계산, 발사, 시간 흐르기(update), 물리 한 스텝, 도착·충돌 처리, 불꽃.
 * 키보드·마우스로 조준하는 부분은 다음 층(Controls)에 있다.
 */
abstract class Flight extends ScreenPainter {
    private static final long serialVersionUID = 1L;

    /** 방향키를 누르고 있는 동안 조준을 움직인다 (Controls 가 만든다) */
    protected abstract void aimWithKeys(double dt);

    /** 발사 전 상태로 되돌린다 (소포, 꼬리, 연료, 얻은 별) */
    protected void resetShot() {
        ship = null;
        trail.clear();
        fuel = Rules.FUEL;
        thrusting = false;
        Sound.thrust(false);
        for (int i = 0; i < 3; i++) gotStar[i] = false;
        previewDirty = true;
    }

    /** 조준선: 궤도의 앞부분만 미리 계산해서 점으로 보여 준다 */
    private void updatePreview() {
        preview.clear();
        previewEnd = Rules.FLYING;
        previewDirty = false;
        if (level == null) return;
        Ship s = new Ship(level, aimAngle, aimPower);
        for (int i = 0; i < level.getPreviewSteps() && previewEnd == Rules.FLYING; i++) {
            previewEnd = s.step(level, false);
            if (i % 4 == 3) preview.add(new double[]{s.getX(), s.getY()});
        }
        if (previewEnd == Rules.CRASHED) preview.add(new double[]{s.getX(), s.getY()});
    }

    protected void launch(double angle, double power) {
        resetShot();
        ship = new Ship(level, angle, power);
        prevX = ship.getX();
        prevY = ship.getY();
        stepDebt = 0;
        state = FLY;
        burst(ship.getX(), ship.getY(), 14, 40, 120, 0.5, 2.4);
        if (!demo) {
            tries++;
            tip = null;
            Sound.launch(power);
        }
    }

    protected void update(double dt) {
        anim += dt;
        aimWithKeys(dt);
        if (state == AIM && previewDirty) updatePreview();
        if (state == FLY) {
            stepDebt += dt;
            while (stepDebt >= Rules.DT && state == FLY) {
                stepDebt -= Rules.DT;
                stepOnce();
            }
        } else if (waitTimer > 0) {
            waitTimer -= dt;
            if (waitTimer <= 0) {
                if (state == DEAD) {
                    state = AIM;
                    resetShot();
                } else if (state == DEMO_WAIT && level != null) {
                    launch(level.getSolutionAngle(), level.getSolutionPower());
                } else if (state == ARRIVE) {
                    state = WIN;
                    Sound.thrust(false);
                }
            }
        }
        particles.removeIf(p -> !p.update(dt));
        for (int i = floaters.size() - 1; i >= 0; i--) {
            Object[] f = floaters.get(i);
            f[3] = (Double) f[3] - dt;
            f[2] = (Double) f[2] - 22 * dt;
            if ((Double) f[3] <= 0) floaters.remove(i);
        }
        if (shake > 0.3) shake *= 0.88; else shake = 0;
    }

    /** 물리 한 스텝 + 그에 따른 게임 처리 (별 얻기, 웜홀, 도착·충돌) */
    private void stepOnce() {
        prevX = ship.getX();
        prevY = ship.getY();
        boolean burn = thrusting && fuel > 0 && !demo;
        if (burn) {
            fuel -= Rules.DT;
            if (ship.getSteps() % 2 == 0) {
                double speed = Math.hypot(ship.getVx(), ship.getVy());
                particles.add(new Particle(ship.getX(), ship.getY(), -ship.getVx() / speed * 150 + (fx.nextDouble() - 0.5) * 90,
                        -ship.getVy() / speed * 150 + (fx.nextDouble() - 0.5) * 90, 0.4, 2.6, 195));
            }
        }
        Sound.thrust(burn);
        int result = ship.step(level, burn);

        if (ship.hasJustWarped()) {
            trail.add(null);
            burst(prevX, prevY, 18, 275, 140, 0.6, 2.4);
            burst(ship.getX(), ship.getY(), 18, 275, 140, 0.6, 2.4);
            prevX = ship.getX();
            prevY = ship.getY();
            if (!demo) Sound.warp();
        }
        trail.add(new double[]{ship.getX(), ship.getY()});

        for (int i = 0; i < 3; i++) {
            double dx = ship.getX() - level.getStarX(i), dy = ship.getY() - level.getStarY(i);
            if (!gotStar[i] && dx * dx + dy * dy < Rules.STAR_R * Rules.STAR_R) {
                gotStar[i] = true;
                gotStarAt[i] = anim;
                burst(level.getStarX(i), level.getStarY(i), 22, 48, 170, 0.8, 2.6);
                if (!demo) Sound.star(starCount() - 1);
            }
        }
        if (result != Rules.FLYING) endFlight(result);
    }

    private void endFlight(int result) {
        thrusting = false;
        Sound.thrust(false);
        if (result == Rules.ARRIVED) {
            burst(level.getTargetX(), level.getTargetY(), 70, 155, 300, 1.3, 3.2);
            burst(level.getTargetX(), level.getTargetY(), 30, 45, 200, 1.1, 2.6);
            if (demo) {
                state = DEMO_WAIT;
                waitTimer = 1.6;
                return;
            }
            Sound.win();
            state = ARRIVE;                 // 불꽃을 잠깐 보여 준 뒤 영수증을 띄운다
            waitTimer = 0.9;
            if (!endless) {
                progress[levelIndex] = Math.max(progress[levelIndex], starCount() + 1);
                Progress.save(progress);
            }
            return;
        }
        ghost = new ArrayList<>(trail);
        if (result == Rules.CRASHED) {
            burst(ship.getX(), ship.getY(), 60, 22, 260, 0.9, 3.4);
            burst(ship.getX(), ship.getY(), 20, 0, 90, 1.2, 4.5);
            if (!demo) {
                shake = 13;
                Sound.crash();
                boolean swallowed = ship.getCrashedInto() instanceof BlackHole;
                addFloater(Lang.t(swallowed ? "삼켜졌습니다" : "파손"), ship.getX(), ship.getY() - 30, Ui.DANGER);
            }
        } else if (!demo) {
            Sound.lost();
            addFloater(Lang.t(ship.getSteps() >= Rules.MAX_STEPS ? "연락 두절" : "우주 미아"), ship.getX(), ship.getY(), new Color(154, 160, 200));
        }
        state = demo ? DEMO_WAIT : DEAD;
        waitTimer = demo ? 1.0 : 0.65;
    }

    private void burst(double x, double y, int count, double hue, double speed, double life, double size) {
        for (int i = 0; i < count; i++) {
            double a = fx.nextDouble() * Math.PI * 2, v = speed * (0.2 + fx.nextDouble());
            particles.add(new Particle(x, y, Math.cos(a) * v, Math.sin(a) * v, life * (0.5 + fx.nextDouble() * 0.5),
                    size * (0.5 + fx.nextDouble()), hue + (fx.nextDouble() - 0.5) * 30));
        }
    }

    private void addFloater(String text, double x, double y, Color color) {
        floaters.add(new Object[]{text, Math.max(80, Math.min(Rules.W - 80, x)), Math.max(60, Math.min(Rules.H - 40, y)), 1.6, color});
    }
}
