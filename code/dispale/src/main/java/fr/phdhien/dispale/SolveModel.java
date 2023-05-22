package fr.phdhien.dispale;

/*
static {
  System.load("/export/home/hien/SOFTWARES/AMPL/amplapi-linux64/amplapi/lib/ampl-2.0.4.0.jar");
  System.load("/export/home/hien/SOFTWARES/AMPL/amplapi-linux64/amplapi/lib/libjavaswigwrapper.so");
}
//*/

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/*
import com.ampl.AMPL;
import com.ampl.Variable;
import com.ampl.DataFrame;
import com.ampl.Environment;
import org.scijava.nativelib.NativeLoader;
//*/

public class SolveModel {
/*
  static {
    System.load("/export/home/hien/SOFTWARES/AMPL/amplapi-linux64/amplapi/lib/ampl-2.0.4.0.jar");
    System.load("/export/home/hien/SOFTWARES/AMPL/amplapi-linux64/amplapi/lib/libjavaswigwrapper.so");
  }
//*/
  
  //public AMPL ampl;
  public int n;
  public double[] w;
  public boolean ok = false;
  public String data = "";
  public String model = "";
  public String dataFilePath = "";
  public String modelFilePath = "";
  public String resultsFilePath = "";
  
  public SolveModel(){
    this.n = 7;
    this.w = new double[7];
    this.ok = false;
    this.dataFilePath = "/home/hien/THESE/programs/eclipse/Flexics_2021/LetSIP/letsip/data/test_model.dat";
    this.modelFilePath = "/home/hien/THESE/programs/eclipse/Flexics_2021/LetSIP/letsip/data/test_model.mod";
    this.resultsFilePath = "/home/hien/THESE/programs/eclipse/Flexics_2021/LetSIP/letsip/results/results.res";
  }
  
  public SolveModel(int n, String dataFilePath, String modelFilePath, String resultsFilePath, String data, String model){
    this.n = n;
    this.w = new double[n];
    this.ok = false;
    this.data = data;
    this.model = model;
    this.dataFilePath = dataFilePath;
    this.modelFilePath = modelFilePath;
    this.resultsFilePath = resultsFilePath;
  }
  
  /*
  public void model(){
      // Interpret the two files
      try {
        ampl.read(modelFilePath);
        ampl.readData(dataFilePath);
      } catch (IOException e) {
        throw new RuntimeException("Failed to load AMPL from within the JAR", e);
      }
      
      ampl.setOption("solver", "cplex");
  }
  //*/
  
  /*
  public void solve(){
    try {
      //ampl = new AMPL();
      // If the AMPL installation directory is not in the system search path:
      //String amplPath = "/export/home/hien/SOFTWARES/AMPL/amplapi-linux64/amplapi";
      String amplPath = "/export/home/hien/SOFTWARES/AMPL/Demo";
      Environment env = new Environment(amplPath);
      this.ampl = new AMPL(env);
      
      model();
      //this.ampl.read(this.modelFilePath);
      //this.ampl.readData(this.dataFilePath);
      //this.ampl.setOption("solver", "cplex");
      
      this.ampl.solve();
      
      // Get the values of the variable Buy in a dataframe object
      Variable weight = this.ampl.getVariable("w");
      DataFrame df = weight.getValues();
      
      this.w = df.getColumnAsDoubles("w.val");
      System.out.print("\nøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøø\n|");
      for(int i=0; i<w.length; i++)
    	  System.out.print(" " + w[i] + " |");
      System.out.println("\nøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøøø\n");
      
      System.out.println("-------------------------------------\n-------------------------------------\n");
      
    } finally {
      ampl.close();
    }
    
  }
  //*/
  public void solve(){
    System.out.println("\n\n$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
    System.out.println("Début de l'execution");
    System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$\n\n");
    
    //writeDataToFile(this.data);
    //writeModelToFile(this.model);
    //*
    try {
      String ampl_prg_path = "/home/hien/THESE/programs/eclipse/Flexics_2021/LetSIP/letsip/lib/launch_ampl.jar";
      String python_prg_path = "/home/hien/THESE/programs/eclipse/Flexics_2021/LetSIP/letsip/scripts/launchJAR.py";
      //String command = "java -jar " + ampl_prg_path;
      //String command = "java -jar " + ampl_prg_path + " " + this.dataFilePath + " " + this.modelFilePath + " " + this.resultsFilePath + " " + n + "";
      //String command = "java -jar " + ampl_prg_path + " " + this.dataFilePath + " " + this.modelFilePath + " " + this.resultsFilePath + " " + n + " " + this.data + " " + this.model;
      String command = "python3 " + python_prg_path + " " + ampl_prg_path + " " + this.dataFilePath + " " + this.modelFilePath + " " + this.resultsFilePath + " " + n + " " + this.data + " " + this.model;
      //String command = "/export/home/hien/SOFTWARES/anaconda/bin/python " + python_prg_path + " " + ampl_prg_path + " " + this.dataFilePath + " " + this.modelFilePath + " " + this.resultsFilePath + " " + n + " " + this.data + " " + this.model;
      //String command = "python " + python_prg_path + " " + ampl_prg_path + " " + this.dataFilePath + " " + this.modelFilePath + " " + this.resultsFilePath + " " + n + " " + this.data + " " + this.model;
      
      Process p = Runtime.getRuntime().exec(command);
      
      System.out.println("Waiting for execution ...");
      
      int finish = p.waitFor();
      if(finish == 0){
        this.ok = true;
        System.out.println("Execution done...\n");
      }
      else {
        this.ok = false;
        System.out.println("Problème pendant l'exécution...");
        System.out.println("Exit status: " + finish + "\n");
      }
    
    } catch (IOException e) {
      // TODO Auto-generated catch block
      this.ok = false;
      System.out.println("*****\nProblem avec RunTime\n*****");
      e.printStackTrace();
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      this.ok = false;
      System.out.println("*****\nProblem pendant l'execution, programme interrompu\n*****");
      e.printStackTrace();
    }
    //*/
    System.out.println("\n\n$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
    System.out.println("Fin de l'execution");
    System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$\n\n");
    
  }

  public void writeDataToFile(String txt) {
    File file = new File(this.dataFilePath);
    FileWriter fr;
    try {
    	//fr = new FileWriter(file, true);
    	fr = new FileWriter(file, false);
    	fr.write(txt);
    	fr.close();
    } catch (IOException e) {
    	// TODO Auto-generated catch block
    	System.out.println("Failed to write in result file");
    	e.printStackTrace();
    }
  }
  
  public void writeModelToFile(String txt) {
    File file = new File(this.modelFilePath);
    FileWriter fr;
    try {
    	//fr = new FileWriter(file, true);
    	fr = new FileWriter(file, false);
    	fr.write(txt);
    	fr.close();
    } catch (IOException e) {
    	// TODO Auto-generated catch block
    	System.out.println("Failed to write in result file");
    	e.printStackTrace();
    }
  }
  
  public double[] getWeight(){
    if(this.ok){
      String ligne = "";
      BufferedReader br;
      try {
        //fr = new FileWriter(file, true);
        br = new BufferedReader(new FileReader(resultsFilePath));
        String tempo = "";
        while ((tempo=br.readLine()) != null) {
          ligne += tempo + "\n";
        }
        br.close();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        System.out.println("Failed to write in result file");
        e.printStackTrace();
      }
      
      ligne = ligne.replace("\n", "");
      System.out.println("\n" + ligne + "\n");
      String[] result = ligne.split(":");
      for(int i=0; i<result.length; i++)
        this.w[i] = Double.parseDouble(result[i]);
        
    }
    else {
      for(int i=0; i<this.n; i++)
        this.w[i] = -1.0;
    }
    
    return this.w;
  }
  
}
