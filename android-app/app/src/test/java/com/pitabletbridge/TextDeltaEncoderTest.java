package com.pitabletbridge;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class TextDeltaEncoderTest {
    @Test
    public void emitsInsertedText() {
        List<KeyCommand> commands = TextDeltaEncoder.computeCommands("ab", "abcd");
        Assert.assertEquals(1, commands.size());
        Assert.assertEquals("text", commands.get(0).kind);
        Assert.assertEquals("cd", commands.get(0).text);
    }

    @Test
    public void emitsBackspaceForDeletion() {
        List<KeyCommand> commands = TextDeltaEncoder.computeCommands("hello", "hel");
        Assert.assertEquals(1, commands.size());
        Assert.assertEquals("backspace", commands.get(0).kind);
        Assert.assertEquals(2, commands.get(0).count);
    }
}

