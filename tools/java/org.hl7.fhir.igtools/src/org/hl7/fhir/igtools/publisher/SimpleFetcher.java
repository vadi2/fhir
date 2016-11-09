package org.hl7.fhir.igtools.publisher;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.dstu3.context.IWorkerContext;
import org.hl7.fhir.dstu3.formats.FormatUtilities;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.StructureDefinition;
import org.hl7.fhir.dstu3.model.Type;
import org.hl7.fhir.dstu3.model.UriType;
import org.hl7.fhir.utilities.Utilities;

public class SimpleFetcher implements IFetchFile {

  private IGKnowledgeProvider pkp;
  private List<String> resourceDirs;
  
  public SimpleFetcher(List<String> resourceDirs) {
    this.resourceDirs = resourceDirs;
  }

  @Override
  public void setPkp(IGKnowledgeProvider pkp) {
    this.pkp = pkp;
  }

  @Override
  public FetchedFile fetch(String path) throws Exception {
    File f = new File(path);
    if (!f.exists())
      throw new Exception("Unable to find file "+path);
    FetchedFile ff = new FetchedFile();
    ff.setPath(path);
    ff.setName(fileTitle(path));
    ff.setTime(f.lastModified());
    if (f.isDirectory()) {
      ff.setContentType("application/directory");
      ff.setFolder(true);   
      for (File fl : f.listFiles())
        ff.getFiles().add(fl.getAbsolutePath());
    } else {
      ff.setFolder(false);   
      if (path.endsWith("json"))
        ff.setContentType("application/fhir+json");
      else if (path.endsWith("xml"))
        ff.setContentType("application/fhir+xml");

      InputStream ss = new FileInputStream(f);
      byte[] b = new byte[ss.available()];
      ss.read(b, 0, ss.available());
      ff.setSource(b);
      ss.close();
    }
    return ff;
  }

  @Override
  public FetchedFile fetchFlexible(String path) throws Exception {
    File f = new File(path+".xml");
    if (!f.exists())
      f = new File(path+".json");
    if (!f.exists())
      throw new Exception("Unable to find file "+path+".xml or "+path+".json");
    FetchedFile ff = new FetchedFile();
    ff.setPath(path);
    ff.setName(fileTitle(path));
    ff.setTime(f.lastModified());
    if (f.getName().endsWith("json"))
      ff.setContentType("application/fhir+json");
    else if (f.getName().endsWith("xml"))
      ff.setContentType("application/fhir+xml");
    
    InputStream ss = new FileInputStream(f);
    byte[] b = new byte[ss.available()];
    ss.read(b, 0, ss.available());
    ff.setSource(b);
    ss.close();
    return ff;
  }

  private String fileTitle(String path) {
    if (path.contains(".")) {
      String ext = path.substring(path.lastIndexOf(".")+1);
      if (Utilities.isInteger(ext))
        return path;
      else
        return path.substring(0, path.lastIndexOf("."));
    } else
      return path;
  }

  @Override
  public boolean canFetchFlexible(String path) throws Exception {
    File f = new File(path+".xml");
    if (!f.exists())
      f = new File(path+".json");
    return f.exists();
  }

  @Override
  public FetchedFile fetch(Type source, FetchedFile src) throws Exception {
    if (source instanceof Reference) {
      String s = ((Reference)source).getReference();
      if (!s.contains("/"))
        throw new Exception("Bad Source Reference '"+s+"' - should have the format [Type]/[id]");
      String type = s.substring(0,  s.indexOf("/"));
      String id = s.substring(s.indexOf("/")+1); 
      if (!pkp.getContext().hasResource(StructureDefinition.class , "http://hl7.org/fhir/StructureDefinition/"+type))
        throw new Exception("Bad Resource Identity - should have the format [Type]/[id] where Type is a valid resource type:" + s);
      if (!id.matches(FormatUtilities.ID_REGEX))
        throw new Exception("Bad Source Reference '"+s+"' - should have the format [Type]/[id] where id is a valid FHIR id type");
      String fn = pkp.getSourceFor(type+"/"+id);
      List<String> dirs = new ArrayList<>();
      dirs.add(Utilities.getDirectoryForFile(src.getPath()));
      dirs.addAll(resourceDirs);
      
      if (Utilities.noString(fn)) {
        // no source in the json file.
        fn = findFile(dirs, type.toLowerCase()+"-"+id+".xml");
        if (fn == null)
          fn = findFile(dirs, type.toLowerCase()+"-"+id+".json");
        if (fn == null)
          fn = findFile(dirs, type.toLowerCase()+"/"+id+".xml");
        if (fn == null)
          fn = findFile(dirs, type.toLowerCase()+"/"+id+".json");
        if (fn == null)
          fn = findFile(dirs, id+".xml");
        if (fn == null)
          fn = findFile(dirs, id+".json");
        if (fn == null)
          throw new Exception("Unable to find the source file for "+type+"/"+id+": not specified, so tried "+type.toLowerCase()+"-"+id+".xml, "+type.toLowerCase()+"-"+id+".json, "+type.toLowerCase()+"/"+id+".xml, "+type.toLowerCase()+"/"+id+".json, "+id+".xml, and "+id+".json in dirs "+dirs.toString());
      } else {
        fn = findFile(dirs, fn);
        if (!exists(fn))
          throw new Exception("Unable to find the source file for "+type+"/"+id+" at "+fn);
      }
      return fetch(fn); 
    } else if (source instanceof UriType) {
      UriType s = (UriType) source;
      String fn = Utilities.path(Utilities.getDirectoryForFile(src.getPath()), s.getValueAsString());
      return fetch(fn); 
    } else {
      throw new Exception("Unknown source reference type for implementation guide");
    }
  }

  private String findFile(List<String> dirs, String name) throws IOException {
    for (String dir : dirs) {
      String fn = Utilities.path(dir, name);
      if (new File(fn).exists())
        return fn;
    }
    return null;
  }

  private boolean exists(String fn) {
    return new File(fn).exists();
  }

  
  @Override
  public List<FetchedFile> scan(String sourceDir, IWorkerContext context) {
    List<FetchedFile> res = new ArrayList<>();
    for (File f : new File(sourceDir).listFiles()) {
      if (!f.isDirectory()) {
        String fn = f.getAbsolutePath();
        String ext = Utilities.getFileExtension(fn);
        boolean ok = false;
        if (!Utilities.existsInList(ext, "json", "ttl", "html", "txt"))
          try {
            org.hl7.fhir.dstu3.elementmodel.Element e = new org.hl7.fhir.dstu3.elementmodel.XmlParser(context).parse(new FileInputStream(f));
            addFile(res, f, "application/fhir+xml");
            ok = true;
          } catch (Exception e) {
            System.out.println(e.getMessage());
          }
        if (!ok && !Utilities.existsInList(ext, "xml", "ttl", "html", "txt")) {
          try {
            org.hl7.fhir.dstu3.elementmodel.Element e = new org.hl7.fhir.dstu3.elementmodel.JsonParser(context).parse(new FileInputStream(fn));
            addFile(res, f, "application/fhir+json");
            ok = true;
          } catch (Exception e) {
            System.out.println(e.getMessage());
          }
        }
        if (!ok && !Utilities.existsInList(ext, "json", "xml", "html", "txt")) {
          try {
            org.hl7.fhir.dstu3.elementmodel.Element e = new org.hl7.fhir.dstu3.elementmodel.TurtleParser(context).parse(new FileInputStream(fn));
            addFile(res, f, "text/turtle");
            ok = true;
          } catch (Exception e) {
            System.out.println(e.getMessage());
          }
        }
      }
    }
    return res;
  }

  private void addFile(List<FetchedFile> res, File f, String cnt) throws IOException {
    FetchedFile ff = new FetchedFile();
    ff.setPath(f.getAbsolutePath());
    ff.setName(fileTitle(f.getAbsolutePath()));
    ff.setTime(f.lastModified());
    ff.setFolder(false);   
    ff.setContentType(cnt);
    InputStream ss = new FileInputStream(f);
    byte[] b = new byte[ss.available()];
    ss.read(b, 0, ss.available());
    ff.setSource(b);
    ss.close();
    res.add(ff);    
  }

}
