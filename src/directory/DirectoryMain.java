package directory;

//main do directory

public class DirectoryMain {

    //Lê o argumento da linha de comando (Porto UDP onde vai ouvir)
    static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Uso: java DirectoryMain <portoUDP>");
            return;
        }

        //Cria uma DirectoryService e chama start
        int porto = Integer.parseInt(args[0]);
        DirectoryService service = new DirectoryService(porto);
        service.start();
    }
}
