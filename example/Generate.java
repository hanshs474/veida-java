import io.github.hanshs474.veida.Veida;

public class Generate {
    public static void main(String[] args) throws Exception {
        String prompt = args.length > 0 ? String.join(" ", args)
                : "a paper boat on a calm lake at sunrise, soft light";
        Veida.Image img = Veida.create().generate(prompt, "16:9");
        System.out.println(img);
    }
}
