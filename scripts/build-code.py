'''
Created on 23 Feb 2023

@author: Lobnury
'''

import os
import sys
import subprocess
import multiprocessing as mp
from multiprocessing import Process
#
nbP = 1 # number of project to build in parallel
#
project_to_build = ["Dispale"]
#
'''
# preparing  project directories directories
project_closedDiv = os.path.join(root_dir, "code", "ClosedDiv")
project_closedDivMat = os.path.join(root_dir, "code", "ClosedDiv-MathieuVavrille")
project_cftp = os.path.join(root_dir, "code", "cftp-sampling")
project_flexics = os.path.join(root_dir, "code", "flexics-sdivjax")
project_Gibbs = os.path.join(root_dir, "code", "gibbs-sampling")
'''
#
#*******************************************************************************************************************
#*******************************************************************************************************************
#
def get_project_dir(project, root_dir):
    project_dir = os.path.join(root_dir, "code")
    #
    return project_dir
#
#*******************************************************************************************************************
#*******************************************************************************************************************
#
def build_gradle(projectDir, log_file, command_):
    os.chdir(projectDir)
    command = "./gradlew -i build"
    if command_=="clean":
        command = "./gradlew -i clean"
    p = subprocess.Popen(command, shell=True, stdout=log_file, stderr=log_file)
    exit_code = p.wait()
    #
    return exit_code
#
#*******************************************************************************************************************
#*******************************************************************************************************************
#
def build_maven(projectDir, log_file, command_):
    abs_project_dir = os.path.abspath(projectDir)
    command_1 = "cd " + projectDir
    command_2 = "mvn package"
    if command_=="clean":
        command_2 = "mvn clean"
    #os.chdir(projectDir)
    p = subprocess.Popen(command_2, cwd=projectDir, shell=True, stdout=log_file, stderr=log_file)
    exit_code = p.wait()
    #
    return exit_code
#
#*******************************************************************************************************************
#*******************************************************************************************************************
#
def select_builder_and_build(projectDir, log_file, command):
    maven_build_file = os.path.join(projectDir, "pom.xml")
    gradle_build_file = os.path.join(projectDir, "build.gradle")
    #
    exit_code = 1
    if os.path.exists(gradle_build_file):
        # builder = "GRADLE"
        exit_code = build_gradle(projectDir, log_file, command)
    elif os.path.exists(maven_build_file):
        # builder = "MAVEN"
        exit_code = build_maven(projectDir, log_file, command)
    #
    return exit_code
#
#*******************************************************************************************************************
#*******************************************************************************************************************
#
def launch_building(queue, project, root_dir, command):
    try:
        project_dir = get_project_dir(project, root_dir)
        if not os.path.exists(project_dir):
            print(f"\nIMPOSSIBLE TO BUILD PROJECT : {project}.\nTHE GIVEN PROJECT DIRECTORY DOES NOT EXISTS...\n")
            sys.exit(1)
        #
        building_log_file = os.path.join(root_dir, project + "-building-LOGS.txt")
        log_file = open(building_log_file, "w")
        #
        exit_code = select_builder_and_build(project_dir, log_file, command.lower())
        #
        if exit_code ==0:
            print(f"\nBUILDING FINISH : EVERYTHING IS OK.\nExit Status : {exit_code}\n")
        else:
            print(f"\nBUILDING PROJECT : {project} FAILED.\nExit Status : {exit_code}\n")
        
    finally:
        queue.put(mp.current_process().name)
    #
    '''
    command = f"python3 build-thread.py {builder} {project_dir}
    p = subprocess.Popen([command], shell=True)
    exit_code = p.wait()
    #subprocess.run([command + "; echo \"Exit status: $?\" "], shell=True, check=True, stdout="", stderr="")
    return exit code
    '''
    #
#
#*******************************************************************************************************************
#*******************************************************************************************************************
#
if __name__ == '__main__':
    #
    command = "build"
    if len(sys.argv) >= 2:
        command = sys.argv[1]
        if command.lower() not in ["build", "clean"]:
            command = "build"
            msg = "\n\nThe value given does not allow either to:\n\t- build: 'command=build'\n"
            msg += "or\n\t- clean: 'command=clean'\nThe default value of command will then be used"
            msg += "--> command = build"
            print(msg)
    #
    # set the root directory
    cwd = os.path.dirname(os.path.realpath(__file__)) # current directory
    root_dir = os.path.realpath(os.path.join(cwd, os.pardir)) # root directory
    #
    #
    # launch processes one after the other, nbP processes can be launched in parallel
    curr = 0
    procs = dict()
    queue = mp.Queue() # queue of ongoing process
    for i in range(0, nbP):
        if curr < len(project_to_build):
            proc_name = "Build " + project_to_build[curr]
            proc = Process(name=proc_name, target=launch_building, args=(queue, project_to_build[curr], root_dir, command,))
            proc.start()
            procs[proc.name] = proc
            curr += 1
    # using the queue, launch a new process whenever an old process finishes its workload
    while procs:
        name = queue.get()
        proc = procs[name]
        print(proc) 
        proc.join()
        del procs[name]
        if curr < len(project_to_build):
            proc_name = "Build " + project_to_build[curr]
            proc = Process(name=proc_name, target=launch_building, args=(queue, project_to_build[curr], root_dir, command,))
            proc.start()
            procs[proc.name] = proc
            curr += 1
    #
    print("\n\n***** FINISHED *****\n\n")
    
    
    
    
    
    #########################################################################################################################################################

