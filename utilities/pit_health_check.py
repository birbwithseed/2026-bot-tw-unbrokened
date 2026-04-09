import time
import sys
import os

try:
    from networktables import NetworkTables
except ImportError:
    print("\033[91mError: pynetworktables is not installed!\033[0m")
    print("Please install it by running:  pip install pynetworktables")
    sys.exit(1)

# ANSI escape codes for coloring
GREEN = '\033[92m'
RED = '\033[91m'
YELLOW = '\033[93m'
CYAN = '\033[96m'
BOLD = '\033[1m'
RESET = '\033[0m'
CLEAR_SCREEN = '\033[2J\033[H'

TEAM_NUMBER = 2026 # Change if team number differs

def get_status_color(value, expected, tolerance=0):
    """Returns a colored string based on whether a value matches an expected target."""
    if isinstance(value, (int, float)) and isinstance(expected, (int, float)):
        if abs(value - expected) <= tolerance:
            return f"{GREEN}[OK]{RESET}"
        return f"{RED}[FAULT]{RESET}"
    
    if value == expected:
        return f"{GREEN}[OK]{RESET}"
    return f"{RED}[FAULT]{RESET}"

def get_boolean_color(value):
    """Returns a green OK for True, red FAULT for False."""
    if value:
         return f"{GREEN}[OK]{RESET}"
    return f"{RED}[FAULT]{RESET}"


def print_dashboard(connected, table):
    """Refreshes the terminal and prints the neatly formatted checklist."""
    # Move cursor to top left instead of clearing full screen to prevent flickering
    sys.stdout.write('\033[H') 
    
    # Header
    print(f"{CYAN}{BOLD}{'='*50}")
    print(f"{' '*10}ROBOT PIT HEALTH MONITOR")
    print(f"{'='*50}{RESET}")

    # Connection Status
    if connected:
        print(f"NetworkTables: {GREEN}CONNECTED{RESET}\n")
    else:
        print(f"NetworkTables: {RED}DISCONNECTED - Waiting for Robot...{RESET}\n")
        # Print blank lines to clear out old data if disconnected
        print(" " * 50 + "\n" * 15) 
        return

    # --- Fetching Values ---
    # In a real robot, you would push these values from your Java Subsystems to SmartDashboard.
    # Ex: SmartDashboard.putBoolean("Turret/Motor_OK", m_turretMotor.getFaults() == 0);
    
    turret_ok = table.getBoolean('Turret/Motor_OK', False)
    turret_temp = table.getNumber('Turret/Motor_Temp', 0.0)
    
    fire_left_ok = table.getBoolean('FireControl/Left_Motor_OK', False)
    fire_right_ok = table.getBoolean('FireControl/Right_Motor_OK', False)
    
    drive_fl_ok = table.getBoolean('Drive/FL_OK', False)
    drive_fr_ok = table.getBoolean('Drive/FR_OK', False)
    drive_bl_ok = table.getBoolean('Drive/BL_OK', False)
    drive_br_ok = table.getBoolean('Drive/BR_OK', False)
    
    navx_connected = table.getBoolean('NavX/Connected', False)
    battery_voltage = table.getNumber('Battery/Voltage', 0.0)

    # --- Formatting the Display ---
    print(f"{BOLD}--- Core Systems ---{RESET}")
    print(f"Battery Voltage      : {battery_voltage:>5.2f}V ", end="")
    if battery_voltage > 12.0:
        print(f"{GREEN}[GOOD]{RESET}")
    elif battery_voltage > 11.5:
        print(f"{YELLOW}[WARNING]{RESET}")
    else:
        print(f"{RED}[CRITICAL]{RESET}")
        
    print(f"NavX Gyroscope       : {get_boolean_color(navx_connected)}")
    
    print(f"\n{BOLD}--- Turret & Fire Control ---{RESET}")
    print(f"Turret SparkMax      : {get_boolean_color(turret_ok)}  (Temp: {turret_temp:.1f}°C)")
    print(f"Fire NEO (Left)      : {get_boolean_color(fire_left_ok)}")
    print(f"Fire NEO (Right)     : {get_boolean_color(fire_right_ok)}")

    print(f"\n{BOLD}--- Drive Base ---{RESET}")
    print(f"Front Left Module    : {get_boolean_color(drive_fl_ok)}")
    print(f"Front Right Module   : {get_boolean_color(drive_fr_ok)}")
    print(f"Back Left Module     : {get_boolean_color(drive_bl_ok)}")
    print(f"Back Right Module    : {get_boolean_color(drive_br_ok)}")
    
    print(f"\n{CYAN}{'-'*50}{RESET}")
    print("Press Ctrl+C to exit. Updates at 10Hz.")

if __name__ == "__main__":
    os.system('cls' if os.name == 'nt' else 'clear') # Initial full clear
    print(f"Attempting to connect to Team {TEAM_NUMBER} robot...")
    
    NetworkTables.initialize(server=f"10.{TEAM_NUMBER // 100}.{TEAM_NUMBER % 100}.2")
    sd = NetworkTables.getTable("SmartDashboard")
    
    try:
        while True:
            connected = NetworkTables.isConnected()
            print_dashboard(connected, sd)
            time.sleep(0.1) # 10Hz refresh
    except KeyboardInterrupt:
        print(f"\n{CYAN}Exiting Health Monitor...{RESET}")
        sys.exit(0)
