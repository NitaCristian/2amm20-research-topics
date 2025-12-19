'''
Created on 23 Feb 2023

@author: Lobnury
'''

from include import *
#
nbP = 1 # number of project to build in parallel
#
project_to_build = ["Dispale"]
#

#
#=====================================================================================
#=====================================================================================
#

def get_argParser():
    """Create a parser for command line arguments

    Returns:
        argparse.ArgumentParser: the parser object
    """
    #
    epilog_ = "Copyrights: Normandie Université & Université de Caen-Normandie & IMT Atlantique - France ** "
    epilog_ += "Contacts: Arnold Hien, Samir Loudni, Abdelkader Ouali, Albrecht Zimmermann\n\n"
    #
    parser:argparse.ArgumentParser = argparse.ArgumentParser(
                                    prog="DiSPaLe project builder",
                                    description="Build DiSPaLe project and generate `jars` file.",
                                    epilog=epilog_,
                                    add_help=False)
    #
    parser.add_argument("-v", "--version", action="version", version="DiSPaLe-0.1")
    parser.add_argument('-h', '--help', action='help', default=argparse.SUPPRESS, help='Display help information')
    #
    msg = "build the project and generate new `jar` file\n"
    parser.add_argument("-b", "--build", action="store_true", help=msg)
    #
    msg = "clean the project and remove the previous `jar` files\n"
    parser.add_argument("-c", "--clean", action="store_true", help=msg)
    #
    msg = "force the building\n"
    parser.add_argument("-f", "--force", action="store_true", help=msg)
    #
    return parser

#
#=====================================================================================
#=====================================================================================
#

def parse_parameters():
    """Parse parameters to be used in the main"""
    parser = get_argParser()
    params = parser.parse_args()
    #
    buildProject:bool = params.build
    cleanProject:bool = params.clean
    forceBuiling:bool = params.force
    #
    return (buildProject, cleanProject, forceBuiling)
#
#=====================================================================================
#=====================================================================================
#

def get_project_dir(project, root_dir):
    project_dir = os.path.join(root_dir, "code")
    #
    return project_dir

#
#=====================================================================================
#=====================================================================================
#

def build_gradle(projectDir:str, command_:str, log_file):
    os.chdir(projectDir)
    command = "./gradlew -i build"
    if command_=="clean":
        command = "./gradlew -i clean"
    p = subprocess.Popen(command, shell=True, stdout=log_file, stderr=log_file)
    exit_code = p.wait()
    #
    return exit_code

#
#=====================================================================================
#=====================================================================================
#

def build_maven(projectDir:str, command_:str, log_file):
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
#=====================================================================================
#=====================================================================================
#

def select_builder_and_compile(projectDir:str, command:str, log_file):
    maven_build_file = os.path.join(projectDir, "pom.xml")
    gradle_build_file = os.path.join(projectDir, "build.gradle")
    #
    exit_code = 1
    if os.path.exists(gradle_build_file):
        # builder = "GRADLE"
        exit_code = build_gradle(projectDir, command, log_file)
    elif os.path.exists(maven_build_file):
        # builder = "MAVEN"
        exit_code = build_maven(projectDir, command, log_file)
    #
    return exit_code

#
#=====================================================================================
#=====================================================================================
#


#
#=====================================================================================
#=====================================================================================
#


if __name__ == '__main__':
    #
    (buildProject, cleanProject, forceBuiling) = parse_parameters()
    #
    (exit_code_1, exit_code_2) = (0, 0)
    (command_1, command_2) = ("echo 'FINISH'", "echo 'FINISH'")
    #
    if not os.path.exists(results_dir): 
        os.makedirs(results_dir)
    #
    compiling_log_file = os.path.join(results_dir, "compiling-LOGS.txt")
    cleaning_log_file = os.path.join(results_dir, "cleaning-LOGS.txt")
    #
    #------------------------------------------------------------------------------------#
    #
    if (not cleanProject) and (not buildProject):
        print(f"\nNO ARGUMENT SET FOR BUILDING OR CLEANING.")
        print(f"Use `-b` for building and `-c` for cleaning. You can force building/cleaning using `-f`.")
        print(f"Type `-h` for more detail\n\nExit Status : 0. \n")
        sys.exit(0)
    elif cleanProject and buildProject:
        command_1 = "mvn clean"
        if forceBuiling:
            #
            log_file = open(cleaning_log_file, "w")
            exit_code_1 = select_builder_and_compile(code_dir, "clean", log_file)
            log_file.close()
            #
            log_file = open(compiling_log_file, "w")
            exit_code_2 = select_builder_and_compile(code_dir, "compile", log_file)
            log_file.close()
            #
        elif check_if_project_is_built():
            print(f"\nTHE PROJECT IS ALREADY BUILT.")
            print(f"YOU SHOULD USE ARGUMENT `-f` OR `--force` TO FORCE THE BUILDING.\n\n")
    elif cleanProject:
        command_1 = "mvn clean"
        #
        log_file = open(cleaning_log_file, "w")
        exit_code_1 = select_builder_and_compile(code_dir, "clean", log_file)
        log_file.close()
        #
    elif buildProject:
        if forceBuiling:
            #
            log_file = open(compiling_log_file, "w")
            exit_code_2 = select_builder_and_compile(code_dir, "compile", log_file)
            log_file.close()
            #
        elif not check_if_project_is_built():
            #
            log_file = open(compiling_log_file, "w")
            exit_code_2 = select_builder_and_compile(code_dir, "compile", log_file)
            log_file.close()
            #
        else:
            print(f"\nTHE PROJECT IS ALREADY BUILT.")
            print(f"YOU SHOULD USE ARGUMENT `-f` OR `--force` TO FORCE THE BUILDING.\n\n")
            print(f"Exit Status : 0.")
            sys.exit(0)
    #
    #------------------------------------------------------------------------------------#
    #
    #
    #------------------------------------------------------------------------------------#
    #
    if (exit_code_1 == 0) and (exit_code_2 == 0):
        print(f"\nBUILDING/CLEANING FINISHED : EVERYTHING IS OK.\nExit Status : 0\n")
    elif (exit_code_1 != 0) or (exit_code_2 != 0):
        print(f"\nBUILDING AND/OR CLEANING PROJECT DiSPaLe FAILED.\n")
        print(f"\nCLEANING Exit Status : {exit_code_1}")
        print(f"\nBUILDING Exit Status : {exit_code_2}\n")
    elif exit_code_1 == 0:
        print(f"\nCLEANING PROJECT DiSPaLe FAILED.\nExit Status : {exit_code_1}\n")
        print(f"\nBUILDING FINISHED CORRECTLY.\nExit Status : 0\n")
    elif exit_code_2 == 0:
        print(f"\nCLEANING FINISHED CORRECTLY.\nExit Status : 0\n")
        print(f"\nBUILDING PROJECT DiSPaLe FAILED.\nExit Status : {exit_code_2}\n")
    #
    #------------------------------------------------------------------------------------#
    #
    #
    print("\n\n***** FINISHED *****\n\n")
    
    
    
    
    
    #########################################################################################################################################################

