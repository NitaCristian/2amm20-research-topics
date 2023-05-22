package fr.phdhien.dispale;

import java.io.IOException;

public class LaunchAMPL {
  public double[] w;
  public String dataFilePath = "";
  public String modelFilePath = "";
  
  //public SolveModel(int n, String dataFilePath, String modelFilePath){
  public LaunchAMPL(int n, String dataFilePath, String modelFilePath){
    
    this.w = new double[n];
    this.dataFilePath = dataFilePath;
    this.modelFilePath = modelFilePath;
    
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
  
  public double[] getWeight(){
    return this.w;
  }
  
}
