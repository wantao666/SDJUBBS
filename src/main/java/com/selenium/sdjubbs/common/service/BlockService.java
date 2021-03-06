package com.selenium.sdjubbs.common.service;

import com.selenium.sdjubbs.common.bean.Block;
import com.selenium.sdjubbs.common.mapper.BlockMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BlockService implements BlockMapper {

    @Autowired
    private BlockMapper blockMapper;

    @Override
    @Transactional(readOnly = true)
    public List<Block> getAllBlock() {
        return blockMapper.getAllBlock();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Block> getAllBlockBySearch(String search) {
        return blockMapper.getAllBlockBySearch(search);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Block> getAllBlockForUser() {
        return blockMapper.getAllBlockForUser();
    }

    @Override
    @Transactional(readOnly = true)
    public Block getBlockById(int id) {
        return blockMapper.getBlockById(id);
    }

    @Override
    @Transactional(rollbackFor = {Exception.class})
    public Integer updateBlock(Block block) {
        return blockMapper.updateBlock(block);
    }

    @Override
    @Transactional(rollbackFor = {Exception.class})
    public Integer addBlock(Block block) {
        return blockMapper.addBlock(block);
    }

    @Override
    @Transactional(readOnly = true)
    public Integer getBlockCount() {
        return blockMapper.getBlockCount();
    }


}
