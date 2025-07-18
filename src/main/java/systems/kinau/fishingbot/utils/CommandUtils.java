package systems.kinau.fishingbot.utils;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import java.util.HashMap;
import java.util.Map;

public class CommandUtils {

//    public static <T> Set<ArgumentType<?>> findUsedArguments(CommandContextBuilder<T> context) {
//        Map<String, ParsedArgument<T, ?>> arguments = getArguments(context);
//        Set<CommandNode<T>> set = Sets.newIdentityHashSet();
//        Set<com.mojang.brigadier.arguments.ArgumentType<?>> set2 = Sets.newHashSet();
//        findUsedArgumentTypes(rootNode, set2, set);
//        return set2;
//    }
//
    public static <T> Map<String, Pair<ArgumentType<?>, ParsedArgument<T, ?>>> getArguments(CommandContextBuilder<T> context) {
        Map<String, Pair<ArgumentType<?>, ParsedArgument<T, ?>>> arguments = new HashMap<>();
        do {
            CommandContextBuilder<T> finalContext = context;
            context.getNodes().stream().filter(node -> node.getNode() instanceof ArgumentCommandNode).forEach(node -> {
                arguments.put(node.getNode().getName(), Pair.of(((ArgumentCommandNode<?, ?>) node.getNode()).getType(), finalContext.getArguments().get(node.getNode().getName())));
            });
        } while ((context = context.getChild()) != null);
        return arguments;
    }

}
