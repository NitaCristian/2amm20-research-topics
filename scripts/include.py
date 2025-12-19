'''
Created on 16 Dec 2025

@author: Lobnury
'''

import os
import sys
import math
import argparse
import subprocess


#
#=====================================================================================
#=====================================================================================
#

# set the root directory
cwd = os.path.dirname(os.path.realpath(__file__)) # current directory
root_dir = os.path.realpath(os.path.join(cwd, os.pardir)) # root directory
results_dir = os.path.realpath(os.path.join(root_dir, "results"))
data_dir = os.path.realpath(os.path.join(root_dir, "data"))
code_dir = os.path.realpath(os.path.join(root_dir, "code"))

#
#=====================================================================================
#=====================================================================================
#

#
thresholds = {
        "lymph.txt" : [0.32],
        "hepatitis.txt" : [0.35],
        "zoo-1.txt" : [0.09],
        "german-credit.txt" : [0.35],
        "vote.txt" : [0.3],
        "soybean.txt" : [0.044],
        "mushroom.txt": [0.3],
        "anneal.txt" : [0.81],
        "ijcai16.txt" : [0.1],
        "chess.txt":[0.3],
        "connect.txt":[0.3],
        "kr-vs-kp.txt":[0.3],
        "splice1.txt":[0.3],
        "foodmart.txt":[0.0]
}
#--------------------------------------------
#--------------------------------------------
thresholds_PARSING = {
        "chess.fimi" : [0.6],
        "foodmart.fimi": [0.0001],
        "mushroom.fimi" : [0.079],
        "lymph.fimi" : [0.32],
        "hepatitis.fimi" : [0.35],
        "heart-cleveland.fimi" : [0.388],
        "kr-vs-kp.fimi" : [0.63],
        "zoo-1.fimi" : [0.09],
        "german-credit.fimi" : [0.35],
        "vote.fimi" : [0.057],
        "soybean.fimi" : [0.044],
        "anneal.fimi" : [0.81],
}
#--------------------------------------------
#--------------------------------------------
nb_items = {
        "anneal" : 94,
        "chess": 76,
        "foodmart": 1559,
        "mushroom": 119,
        "splice1": 287,
        "hepatitis" : 68,
        "heart-cleveland" : 95,
        "german-credit" : 112,
        "kr-vs-kp" : 74,
        "lymph" : 68,
        "soybean" : 50,
        "vote" : 48,
        "zoo-1" : 36,
}

#
#=====================================================================================
#=====================================================================================
#

def check_if_directory_exists(dir_path)-> bool:
    return os.path.exists(dir_path)

def check_if_file_exists(file_path)-> bool:
    return os.path.exists(file_path)

#
#-----------------------------------------------------#
#

#
#=====================================================================================
#=====================================================================================
#

def check_if_project_is_built()->bool:
    """check the code directory to verify that the project has been built

    Returns:
        bool: the build status of the code: True or False
    """
    #
    is_built = False
    project_dir = os.path.realpath(os.path.join(code_dir, "dispale", "build", "libs"))
    jarfile = os.path.realpath(os.path.join(project_dir, "dispale-0.0.1-SNAPSHOT.jar"))
    if os.path.isdir(project_dir) and os.path.isfile(jarfile):
        is_built = True
    #
    return is_built

#
#=====================================================================================
#=====================================================================================
#




