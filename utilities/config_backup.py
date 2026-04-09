#!/usr/bin/env python3
import time
import sys
import os
import json
import argparse
import glob
from datetime import datetime

import tkinter as tk
from tkinter import messagebox

try:
    from networktables import NetworkTables
except ImportError:
    print("\033[91mError: pynetworktables is not installed!\033[0m")
    print("Please install it by running:  pip install -r requirements.txt")
    sys.exit(1)

TEAM_NUMBER = 2026  # Change if team number differs
SERVER_IP = f"10.{TEAM_NUMBER // 100}.{TEAM_NUMBER % 100}.2"
BACKUP_DIR = os.path.join(os.path.dirname(__file__), "pid_config_backups")

# Values we explicitly do NOT want to backup because they are live telemetry, not configuration
IGNORED_ENDINGS = [
    "_Temp", "_OK", "Connected", "Voltage", "Speed Output", "Position", "Errors"
]

def is_tunable_config(key_name):
    """Filters out live telemetry data so we only save tunables/configs."""
    for ending in IGNORED_ENDINGS:
        if key_name.endswith(ending):
            return False
    return True

def connect_to_robot():
    """Connects to NetworkTables and blocks until connection is established."""
    NetworkTables.initialize(server=SERVER_IP)
    
    print(f"Connecting to Robot {TEAM_NUMBER} at {SERVER_IP}...")
    # Wait up to 5 seconds to connect
    for _ in range(50):
        if NetworkTables.isConnected():
            print("\033[92m[OK] Connected to NetworkTables!\033[0m\n")
            return NetworkTables.getTable("SmartDashboard")
        time.sleep(0.1)
        
    raise ConnectionError("Could not connect to the Robot. Is it turned on?")

def backup_config(table):
    """Reads all tunable variables from SmartDashboard and saves them to a JSON file."""
    config_data = {}
    
    # Get all keys currently in the SmartDashboard table
    keys = table.getKeys()
    
    if not keys:
        raise ValueError("SmartDashboard is empty! No configuration found to backup.")
    
    print("Extracting configuration values...")
    for key in keys:
        if is_tunable_config(key):
            value = table.getValue(key, None)
            if value is not None:
                config_data[key] = value
                print(f"  Found: {key} = {value}")
            
    if not config_data:
        raise ValueError("No tunable configurations found after filtering telemetry.")
        
    # Generate timestamped filename
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    filename = f"config_backup_{timestamp}.json"
    
    os.makedirs(BACKUP_DIR, exist_ok=True)
    filepath = os.path.join(BACKUP_DIR, filename)
    
    with open(filepath, 'w') as f:
        json.dump(config_data, f, indent=4)
        
    print(f"\n\033[92m[SUCCESS] Backed up {len(config_data)} variables to {filename}\033[0m")
    return filename

def restore_config(table, filename):
    """Reads a JSON backup file and pushes all variables back into NetworkTables."""
    filepath = os.path.join(BACKUP_DIR, filename)
    
    if not os.path.exists(filepath):
        raise FileNotFoundError(f"File not found: {filename}")
        
    print(f"Reading configuration from {filename}...")
    with open(filepath, 'r') as f:
        try:
            config_data = json.load(f)
        except json.JSONDecodeError:
            raise ValueError(f"Failed to parse {filename}. Is it valid JSON?")
            
    print(f"Pushing {len(config_data)} variables back to Robot {TEAM_NUMBER}...")
    restored_count = 0
    for key, value in config_data.items():
        is_success = table.putValue(key, value)
        if is_success:
             print(f"  Restored: {key} -> {value}")
             restored_count += 1
        else:
             print(f"  \033[93m[WARNING] Failed to restore: {key}\033[0m")
             
    # Wait a tiny bit to ensure changes flush over the network
    NetworkTables.flush()
    time.sleep(0.5)
    
    print(f"\n\033[92m[SUCCESS] Restore complete!\033[0m")
    return restored_count


# ==========================================
# GUI IMPLEMENTATION
# ==========================================

def launch_gui():
    """Builds and launches a basic 'first time with Tkinter' style GUI."""
    root = tk.Tk()
    root.title("Robot Config Tool")
    root.geometry("400x350")
    
    # Refresh function to grab latest json files
    def get_backup_files():
        os.makedirs(BACKUP_DIR, exist_ok=True)
        search_pattern = os.path.join(BACKUP_DIR, "config_backup_*.json")
        files = [os.path.basename(f) for f in glob.glob(search_pattern)]
        return sorted(files, reverse=True) # Newest first

    # Setup connection state
    try:
        sd_table = connect_to_robot()
        connection_text = f"Connected to Robot {TEAM_NUMBER} ✅"
        connection_color = "green"
    except Exception as e:
        sd_table = None
        connection_text = f"DISCONNECTED ❌\n({e})"
        connection_color = "red"

    # UI Elements
    tk.Label(root, text=connection_text, fg=connection_color, font=("Arial", 12, "bold")).pack(pady=10)

    # 1. HUGE BACKUP BUTTON
    def gui_do_backup():
        if not sd_table:
            messagebox.showerror("Error", "Not connected to robot!")
            return
        
        try:
            filename = backup_config(sd_table)
            messagebox.showinfo("Success!", f"Successfully backed up configuration to:\n{filename}")
            # Refresh the dropdown
            files = get_backup_files()
            menu = file_dropdown["menu"]
            menu.delete(0, "end")
            for file in files:
                menu.add_command(label=file, command=tk._setit(selected_file, file))
            if files: selected_file.set(files[0])
            
        except Exception as e:
            messagebox.showerror("Backup Failed", str(e))

    backup_btn = tk.Button(root, text="BACKUP\nROBOT CONFIG", font=("Helvetica", 18, "bold"), bg="lightblue", width=20, height=3, command=gui_do_backup)
    backup_btn.pack(pady=15)

    # 2. SELECTION DROPDOWN
    tk.Label(root, text="Select backup to restore:").pack()
    selected_file = tk.StringVar(root)
    
    backup_files = get_backup_files()
    if backup_files:
        selected_file.set(backup_files[0])
    else:
        selected_file.set("No backups found!")

    file_dropdown = tk.OptionMenu(root, selected_file, *backup_files if backup_files else ["No backups found!"])
    file_dropdown.config(width=25)
    file_dropdown.pack(pady=5)

    # 3. HUGE RESTORE BUTTON
    def gui_do_restore():
        if not sd_table:
            messagebox.showerror("Error", "Not connected to robot!")
            return
            
        target_file = selected_file.get()
        if target_file == "No backups found!" or not target_file:
            messagebox.showerror("Error", "No backup selected")
            return
            
        confirm = messagebox.askyesno("Confirm Restore", f"Are you sure you want to completely overwrite the robot tuning variables with the contents of:\n{target_file}?")
        if confirm:
            try:
                count = restore_config(sd_table, target_file)
                messagebox.showinfo("Success!", f"Successfully restored {count} variables directly to the robot memory!")
            except Exception as e:
                messagebox.showerror("Restore Failed", str(e))

    restore_btn = tk.Button(root, text="RESTORE\nSELECTED CONFIG", font=("Helvetica", 18, "bold"), bg="lightgreen", width=20, height=3, command=gui_do_restore)
    restore_btn.pack(pady=15)

    root.mainloop()


if __name__ == "__main__":
    # If standard user simply double-clicks or runs `python3 config_backup.py`, length is 1.
    if len(sys.argv) == 1:
        launch_gui()
    else:
        # Fall back to command line arguments for headless pipelines
        parser = argparse.ArgumentParser(description="Backup or Restore tuning variables via SmartDashboard")
        subparsers = parser.add_subparsers(dest="mode", required=True)
        
        backup_parser = subparsers.add_parser("backup", help="Saves current tuning variables to a JSON file")
        
        restore_parser = subparsers.add_parser("restore", help="Pushes a JSON config file back to the robot")
        restore_parser.add_argument("file", help="The config_backup_YYYYMMDD_HHMMSS.json file to restore")
        
        args = parser.parse_args()
        
        try:
            sd_table = connect_to_robot()
            if args.mode == "backup":
                backup_config(sd_table)
            elif args.mode == "restore":
                restore_config(sd_table, args.file)
        except Exception as e:
            print(f"\033[91m[ERROR] {e}\033[0m")
            sys.exit(1)
