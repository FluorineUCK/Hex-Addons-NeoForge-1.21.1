package org.net.eu.pool.mica.mixin;

import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import scala.Product;
import scala.runtime.FunctionXXL;

import java.io.Serializable;

@Mixin({forloop(n,1,22,<[[<[[scala.Function]]>n<[[.class, ]]>]]>)FunctionXXL.class, Product.class, Identifier.class})
public class SerializableMixin implements Serializable {}
