import pygame
import sys
import os
import math
import argparse

# MUST be set before pygame initializes to read joystick when window is not focused
os.environ['SDL_JOYSTICK_ALLOW_BACKGROUND_EVENTS'] = '1'

try:
    from networktables import NetworkTables
except ImportError:
    NetworkTables = None

def connectionListener(connected, info):
    print(info, "; Connected=", connected)

def main():
    parser = argparse.ArgumentParser(description="Turret Simulation Visualizer")
    parser.add_argument('--standalone', action='store_true', help="Run independently with a physical joystick, ignoring the robot code.")
    args = parser.parse_args()

    print("Starting Turret Simulation Python Utility...")
    
    standalone = args.standalone
    sd = None
    joystick = None

    pygame.init()
    pygame.joystick.init()
    if standalone:
        if pygame.joystick.get_count() > 0:
            joystick = pygame.joystick.Joystick(0)
            joystick.init()
            print(f"Standalone Mode: Connected to {joystick.get_name()}")
        else:
            print("Standalone Mode: No joystick found! Please plug one in.")
    elif NetworkTables is not None:
        # Initialize NetworkTables client to connect to local robot code simulation
        NetworkTables.initialize(server='127.0.0.1')
        NetworkTables.addConnectionListener(connectionListener, immediateNotify=True)
        sd = NetworkTables.getTable('SmartDashboard')
    else:
        print("NetworkTables not installed, falling back to standalone if requested.")

    width, height = 700, 700
    try:
        screen = pygame.display.set_mode((width, height))
        pygame.display.set_caption("Turret Tracker" + (" (Standalone Mode)" if standalone else " (Network Mode)"))
    except Exception as e:
        print(f"Error setting display mode: {e}")
        sys.exit(1)
        
    clock = pygame.time.Clock()
    font = pygame.font.SysFont(None, 32)
    small_font = pygame.font.SysFont(None, 24)
    
    center_x, center_y = width // 2, height // 2 - 50
    turret_radius = 50
    barrel_length = 150
    
    angle_deg = 0.0
    is_firing = False
    intake_speed = 0.0
    auto_aim = False
    unjam = False
    
    preset_angles = {
        5: -180, 6: -90, 7: 0, 8: 90, 9: 180, 10: 270
    }
    
    while True:
        # Pumping event queue handles both quitting and background joystick reading
        for event in pygame.event.get():
            if event.type == pygame.QUIT:
                pygame.quit()
                sys.exit()
                
        if standalone:
            if joystick:
                turn_axis = joystick.get_axis(0) # X axis
                fire_axis = joystick.get_axis(1) # Y axis (firing speed)
                throttle_axis = joystick.get_axis(2) if joystick.get_numaxes() > 2 else 0 # Throttle slider
                fire_btn = joystick.get_button(0) # Joystick Trigger
                auto_aim = joystick.get_button(1) if joystick.get_numbuttons() > 1 else False
                unjam = joystick.get_button(11) if joystick.get_numbuttons() > 11 else False

                # Handle Presets
                for btn_id, target_ang in preset_angles.items():
                    if joystick.get_numbuttons() > btn_id and joystick.get_button(btn_id):
                        angle_deg = target_ang

                # Apply simple deadband
                if abs(turn_axis) < 0.1: turn_axis = 0
                
                # Update angle
                angle_deg += turn_axis * 3.0 # degrees per frame
                is_firing = fire_btn
                fire_strength = abs(fire_axis)
                intake_speed = (throttle_axis * -1 + 1) / 2 # Scale -1,1 to 0,1
        else:
            # Fetch NT4 data from Robot WPILib simulation
            if sd:
                angle_deg = sd.getNumber('Sim_TurretAngle', 0.0)
                is_firing = sd.getBoolean('Sim_IsFiring', False)
        
        angle_rad = math.radians(angle_deg)
        end_x = center_x + barrel_length * math.cos(angle_rad)
        end_y = center_y + barrel_length * math.sin(angle_rad)
        
        # Render
        screen.fill((20, 20, 30))
        
        # Draw Base
        pygame.draw.circle(screen, (80, 80, 90), (center_x, center_y), turret_radius + 20)
        
        # Draw Firing Laser if active
        if is_firing:
            laser_end_x = center_x + 800 * math.cos(angle_rad)
            laser_end_y = center_y + 800 * math.sin(angle_rad)
            laser_thickness = max(2, int(fire_strength * 20)) if standalone and joystick else 8
            pygame.draw.line(screen, (255, 50, 50), (end_x, end_y), (laser_end_x, laser_end_y), laser_thickness)
            fire_text = font.render(f"🔥 FIRING 🔥", True, (255, 50, 50))
            screen.blit(fire_text, (center_x - fire_text.get_width()//2, 50))
            
        # Draw Turret Body
        pygame.draw.circle(screen, (150, 150, 160), (center_x, center_y), turret_radius)
        
        # Draw Barrel
        pygame.draw.line(screen, (200, 200, 200), (center_x, center_y), (end_x, end_y), 15)
        
        # Info Text
        text = font.render(f"Turret Angle: {angle_deg:.1f}°", True, (255, 255, 255))
        screen.blit(text, (20, 20))
        
        if standalone:
            # Draw extra standalone state
            y_off = height - 120
            
            # Intake Gauge
            pygame.draw.rect(screen, (50, 50, 50), (20, y_off, 200, 20))
            pygame.draw.rect(screen, (50, 255, 50), (20, y_off, 200 * intake_speed, 20))
            screen.blit(small_font.render(f"Intake Target Speed ({intake_speed:.2f})", True, (200, 200, 200)), (20, y_off - 25))
            
            y_off += 40
            state_str = "States:  "
            state_str += "[Auto-Aim ENABLED]  " if auto_aim else "[Auto-Aim OFF]  "
            state_str += "[UNJAMMING INTAKE]  " if unjam else ""
            screen.blit(small_font.render(state_str, True, (255, 200, 100)), (20, y_off))
            
            y_off += 30
            screen.blit(small_font.render("Presets active: " + " ".join([f"B{b}" for b in preset_angles if joystick and joystick.get_numbuttons() > b and joystick.get_button(b)]), True, (150, 200, 255)), (20, y_off))

        # Mode indicator
        mode_str = "Standalone (Direct Joystick Input)" if standalone else "NetworkTables (WPILib Connection)"
        mode_text = small_font.render(f"Mode: {mode_str}", True, (150, 150, 150))
        screen.blit(mode_text, (20, 50))
        
        if standalone and not joystick:
            warn_text = small_font.render("WARNING: No Joystick Connected!", True, (255, 100, 100))
            screen.blit(warn_text, (20, 80))

        pygame.display.flip()
        clock.tick(60)

if __name__ == "__main__":
    main()
