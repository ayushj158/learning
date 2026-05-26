package heap;
import java.security.Timestamp;
import java.util.PriorityQueue;

public class HospitalTriage {

    public static void main(String[] args){
        HospitalTriage triage = new HospitalTriage();
        triage.addPatient("Alice", 8, 1);
        triage.addPatient("Bob",   5, 2);
        triage.addPatient("Carol", 8, 3);
        triage.addPatient("Dave",  9, 4);
        triage.addPatient("Ramu",  8, 2);

        System.out.println(triage.nexPatient());
        System.out.println(triage.nexPatient());
        System.out.println(triage.nexPatient());
        System.out.println(triage.nexPatient());
    }

    PriorityQueue<Patient> heap;

    public HospitalTriage(){
        heap = new PriorityQueue<>((a,b) -> {
            if(a.severity == b.severity) return Integer.compare(a.arrivalTime, b.arrivalTime);
            
            return Integer.compare(b.severity, a.severity);
        });
    }

    public void addPatient(String name,int severity, int arrivalTime){
        Patient patient = new Patient(name , severity, arrivalTime);
        heap.offer(patient);
        System.out.println(heap);
    }

    public Patient nexPatient(){
        return heap.poll();
    } 

}

class Patient {
    public String name;
    public int severity;
    public int arrivalTime;

    public Patient(String name,int severity, int arrivalTime){
        this.name = name;
        this.severity = severity;
        this.arrivalTime = arrivalTime;
    }

    public String toString(){
        return "Name=" + name + " severity=" + severity + " arrivalTime=" + arrivalTime;
    }
}
